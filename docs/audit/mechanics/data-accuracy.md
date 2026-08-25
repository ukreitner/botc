# Data accuracy — bundled dataset vs current official data (mechanics)

Scope: `engine/src/main/resources/botc/data/characters.json` (171 entries),
`night_and_jinxes.json` (58 jinxes + the two global night-order lists),
`night_guide.json` (116 entries) and the four `raw_*.json` shards, checked
character-by-character against the current official data for all 171 ids.

## Official rules (sources)

**Primary ground truth — The Pandemonium Institute's own published data files**
(the same files the official app and the Script Tool ship):

| file | URL | last updated |
|---|---|---|
| roles (181 characters: id, name, team, edition, ability, setup, reminders, remindersGlobal, first/otherNightReminder, special) | `https://raw.githubusercontent.com/ThePandemoniumInstitute/botc-release/main/resources/data/roles.json` | 2026-06-24 |
| jinxes (131 jinxes) | `.../resources/data/jinxes.json` | 2026-07-08 |
| nightsheet (firstNight 80 / otherNight 99, incl. `dusk`/`minioninfo`/`demoninfo`/`dawn`) | `.../resources/data/nightsheet.json` | 2026-05-11 |

Repo index: `https://github.com/ThePandemoniumInstitute/botc-release/blob/main/resources/index.md`.
Also available: `resources/data/script-schema.json` (the official custom-script schema)
and `https://raw.githubusercontent.com/ThePandemoniumInstitute/botc-translations/main/game/en.json`
(the official app's English strings: `roles`, `reminders`, `jinxes`, `editions`).

**Secondary / confirming** — the wiki, via the MediaWiki API (much cheaper and more
reliable than scraping HTML):

```
https://wiki.bloodontheclocktower.com/api.php?action=parse&page=<Page_Name>&prop=wikitext&format=json
```

Every ability-text change listed below was independently confirmed against the wiki
page's `== Summary ==` quote. Note: **`https://wiki.bloodontheclocktower.com/Night_Order`
does not exist** (the API returns `missingtitle`, and a wiki search for "night order"
returns nothing). `nightsheet.json` above is the authoritative machine-readable night order.

Two structural facts about the current official data that the app has not caught up with:

1. **A new character type exists: `loric`** (11 characters). It is a sibling of `fabled`,
   not a subset. Three characters the app files under `fabled` — Bootlegger, Gardener,
   Storm Catcher — are officially `loric`, and 8 more `loric` characters do not exist in
   the app at all.
2. **Official ability text is US-spelled** ("neighbors", "neighboring") and uses `&`,
   curly apostrophes and curly quotes. The app's text is the older British-spelled
   `bra1n/townsquare` copy.

## What the app does today

- `GameData.kt:19-63` loads `characters.json` and `night_and_jinxes.json`; the built-in
  scripts are `charactersOf("tb"|"bmr"|"sv")` and travellers are matched by `edition`.
- `Character.kt:8-27` — `Team` has exactly `TOWNSFOLK, OUTSIDER, MINION, DEMON, TRAVELLER("traveler"), FABLED`.
  **There is no `LORIC`**, so official `loric` entries cannot be deserialised as-is.
- `Character.kt:62` — `allReminders = reminders + remindersGlobal`; this list is the *only*
  source of tokens in `ReminderPicker` (`SeatSheet.kt:536,561`) and the night tray
  (`NightScreen.kt:202,285`).
- `NightScreen.kt:319-341` already implements **multi-copy token semantics**:
  `availableCopies = character.allReminders.count { it == label }`; ≤1 copy ⇒
  `placeExclusiveReminder` (the token moves), >1 ⇒ up to N simultaneous copies with FIFO
  recycling. The mechanism is correct; **the data never lists a label twice**, so every
  multi-copy token in the game behaves as a single exclusive token.
- `NightOrder.kt:52-190` walks the two id lists; markers are `DUSK / MINION_INFO /
  DEMON_INFO / DAWN`; a step is emitted only if a seat holds the id, or the id is an
  activated fabled.
- `NightGuide.kt` + `night_guide.json` — 116 entries, exactly the 116 ids that appear in
  the app's two night lists. There is no guide channel for day, setup or passive abilities.
- `GameActions.kt:218-242` — `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK` are hard-coded
  `(sourceId, label)` pairs that must match `characters.json` labels exactly (case-sensitive
  `==` inside `clearEphemeral`, `GameActions.kt:248`).
- The four `raw_*.json` files (47 + 58 + 30 + 36 = 171 entries) are **not referenced by any
  code** — `grep -rn "raw_" engine/src app/src web/src tools` returns nothing. They are the
  shards `characters.json` was assembled from, and they have since diverged (see D1).

**Works, verified:** the 119 prepared show cards in `night_guide.json` all use valid `kind`
(`message`/`token`/`good`/`evil`) and `token` (`""`/`self`/`pick`) values and all carry a
label — `NightGuide.VALID_KINDS`/`VALID_TOKENS` would reject none of them — and no step whose
official procedure requires showing an info token (`*YOU ARE*`, `*THIS CHARACTER SELECTED
YOU*`, `*THESE CHARACTERS ARE NOT IN PLAY*`, `*THESE ARE YOUR MINIONS*`, `*THIS PLAYER IS*`)
is missing a card. `GameData.activeJinxes` is already order-insensitive. Every character with
a non-empty night reminder is in the matching night list and vice versa, in both datasets.
Everything else about the loading path works.

## Defects and gaps

### P0

1. **P0 · `characters.json` has been hand-regressed away from its own source shards** —
   `characters.json` differs from the concatenation of `raw_*.json` in exactly six places,
   and in five of them **the raw shard is right and `characters.json` is wrong**:
   `engine/src/main/resources/botc/data/characters.json` vs `raw_exp_evil_outsiders.json` /
   `raw_sv_travellers_fabled.json`.

   | id | `characters.json` (shipping, wrong) | `raw_*.json` (correct, matches official) |
   |---|---|---|
   | `riot` | ability "Nominees die, but may nominate again immediately (on day 3, they must). After day 3, evil wins. [All Minions are Riot]"; `setup: true` | "On day 3, Minions become Riot & nominees die but nominate an alive player immediately. This must happen."; `setup: false` |
   | `sentinel` | `setup: false` | `setup: true` |
   | `bishop` | "…At least 1 opposing player…" | "…At least 1 opposite player…" |
   | `pithag` | "…(if not in play)…" | "…(if not-in-play)…" |
   | `drunk` | `remindersGlobal: ["Is the Drunk"]` | `["Drunk"]` (official: `["Is The Drunk"]` — both are wrong, see D9) |
   | `wraith` | **absent** | present (a Minion, revealed 2025-07-03) |

   Reproduce: pick Riot in Setup — `Setup.modifierFor` (`Setup.kt:121-135`) sees
   `setup = true`, matches the stale `[All Minions are Riot]` bracket against
   `TEAM_WARPING_IDS`/`bracketRegex`, and emits a modifier that relaxes bag validation for a
   rule that no longer exists; the night-step text also describes the pre-revision Riot.
   Pick Sentinel — the ±1 Outsider distribution never appears at all, because
   `Setup.modifierFor` returns `null` immediately for `setup == false` (`Setup.kt:122`).

2. **P0 · The jinx list is 44% complete and a third of what is there is stale.** Official:
   131 jinxes. Bundled: 58. **80 are missing, 39 have outdated text, 7 no longer exist.**
   Among the missing are jinxes that change who wins: `riot+mayor`, `riot+atheist`,
   `leviathan+grandmother`, `leviathan+mayor` (the bundled `leviathan+mayor` text is wrong
   too), every `boffin+*`, every `summoner+*` except two, all seven `alchemist+*`, and every
   `plaguedoctor+*`. Full replacement array in §4 below.

3. **P0 · Riot and Leviathan jinxes describe a rules model that no longer exists.** The
   bundled texts say things like "If Riot kills the Farmer, a good player becomes a Farmer
   tonight." The current official texts are
   *"Each night\*, Riot chooses an alive good player (different to previous nights): a chosen
   Farmer uses their ability but does not die."* — a completely different, ST-driven
   mechanic (`riot+farmer`, `riot+ravenkeeper`, `riot+sage`, `riot+banshee`,
   `leviathan+farmer`, `leviathan+ravenkeeper`, `leviathan+sage`, `leviathan+banshee`,
   `riot+grandmother`). A storyteller following the app here runs the game wrong.

4. **P0 · Storm Catcher's night procedure in the data is the wrong ability.**
   `characters.json` `stormcatcher.firstNightReminder` = *"Mark a good player as \"Safe\".
   Wake each evil player and show them the marked player."* Official:
   *"Announce which character is stormcaught. If that character is in play, mark that player
   as **STORMCAUGHT**. Wake each evil player and show them the character token, then the
   marked player. If not in play, wake each evil player, show them the **THESE CHARACTERS
   ARE NOT IN PLAY** token & the relevant character token."* The app never handles the
   "named character is not in play" branch at all — which is half of what the Storm Catcher
   is for — and it uses the token label `Safe`, which collides with the Monk's `Safe`
   in `EXPIRES_AT_DAWN` (`GameActions.kt:219`): a Storm Catcher `Safe` token is silently
   deleted at dawn even though its protection lasts all game. Official label is `Stormcaught`.

5. **P0 · night_guide.json states rules that contradict the wiki — in 30 places.** All 116
   entries were checked page-by-page against the wiki's Summary / How to Run / Examples:
   **30 P0 contradictions, 51 P1 omissions of a mandatory step and 55 P2 clarity defects,
   across 99 of the 116 characters**; only 17 entries are clean. Full table and the three
   underlying failure patterns in §5.1. The worst four:
   - `undertaker`: *"even if they did not die from it"* — wiki: *"The player must have died
     from execution for the Undertaker to learn who they are."*
   - `vortox`: *"If the Vortox is drunk or poisoned, no one dies, but information is still
     false."* — a drunk/poisoned Vortox has **no ability at all**; info is not forced false
     and the no-execution loss condition does not apply. (The wiki's "even if they are drunk
     or poisoned, it must be false" is about the *Townsfolk*, not the Vortox.)
   - `shabaloth`: *"they learn no new information and their once-per-game abilities remain as
     they were"* — wiki: *"The regurgitated player regains their ability, even a 'once per
     game' ability already used. If they had a 'first night only' or 'start knowing'
     ability, they may use it again."* and *"They wake later tonight if they normally would.
     If they wake on the first night only, they wake now to use their ability."*
   - `butler`: *"This still applies even if the Butler is drunk or poisoned."* — a
     drunk/poisoned Butler has no ability and may vote freely; the ST simply tallies the vote.

6. **P0 · 10 official characters are missing from the dataset**, including one Minion
   (**Wraith**, revealed 2025-07-03 — and it *is* present in `raw_exp_evil_outsiders.json`,
   so it was dropped by hand), one Traveller (**Cacklejack**) and eight Loric. Full entries
   in §2.

7. **P0 · Setup flags wrong on 5 characters**, so `Setup.modifierFor()` mis-computes the
   bag: `sentinel` (false → **true**), `deusexfiasco` (false → **true**), `bootlegger`
   (false → **true**), `gardener` (false → **true**), `riot` (true → **false**).

### P1

8. **P1 · 17 multi-copy reminder tokens are listed only once, so only one copy can ever be
   placed.** `NightScreen.kt:319-341` supports N copies, keyed on how many times the label
   appears in `allReminders`, and the data proves the mechanism works — six entries already
   list duplicates correctly (`amnesiac ?×3`, `knight Know×2`, `noble Know×3`,
   `preacher No Ability×3`, `wizard ?×2`, `yaggababble Dead×3`). The other seventeen were
   collapsed to one copy each:

   | id | app | official | consequence today |
   |---|---|---|---|
   | `innkeeper` | `["Protected","Drunk"]` | `["Safe","Safe","Drunk"]` | the Innkeeper protects **two** players; marking the 2nd moves the token off the 1st |
   | `tealady` | `["Can not die"]` | `["Cannot Die","Cannot Die"]` | both neighbours are protected; only one can be marked |
   | `po` | `["Dead","3 attacks"]` | `["Dead","Dead","Dead","3 Attacks"]` | a charged Po kills 3; only 1 `Dead` token exists |
   | `shabaloth` | `["Dead","Alive"]` | `["Dead","Dead","Alive"]` | Shabaloth kills 2 per night |
   | `pukka` | `["Poisoned","Dead"]` | `["Poisoned","Poisoned","Dead"]` | Pukka poisons a new target while the old one is still poisoned — **exactly the Pukka case the user hit** |
   | `vigormortis` | `["Dead","Has ability","Poisoned"]` | `["Dead","Has Ability"×3,"Poisoned"×3]` | one pair per killed Minion |
   | `nodashii` | `["Dead","Poisoned"]` | `["Dead","Poisoned","Poisoned"]` | poisons **2** Townsfolk neighbours |
   | `harlot` | `["Dead"]` | `["Dead","Dead"]` | Harlot and target can both die |
   | `angel` | `["Protect","Something Bad"]` | `["Protected","Protected","Something Bad"]` | |
   | `duchess` | `["Visitor","False Info"]` | `["Visitor","Visitor","False Info"]` | the Duchess has 2 visitors |
   | `juggler` | `["Correct"]` | `["Correct"]×5` | up to 5 guesses |
   | `mathematician` | `["Abnormal"]` | `["Abnormal"]×5` | |
   | `lunatic` | `["Attack 1","Attack 2","Attack 3"]` | `["Chosen","Chosen","Chosen"]` | also a rename (D9) — three fake attacks |
   | `revolutionary` | `["Used"]` | `["Register Falsely?","Aligned","Aligned"]` | the pair must both be marked |
   | `barista` | `["Sober & Healthy","Ability twice"]` | `["Sober & Healthy","Acts Twice","?","?"]` | |
   | `bootlegger` | `[]` | `["?","?"]` | |

   Reproduce: Bad Moon Rising, Innkeeper. Night 2, tap `Protected`, tap seat A, tap
   `Protected`, tap seat B — A's token vanishes. Same for Pukka's `Poisoned`.

9. **P1 · Reminder labels are inconsistently cased inside `characters.json` itself, and two
   code call sites use a case that no data entry uses.**
   - `characters.json` contains **both** `"No ability"` (slayer, virgin, courtier, fool,
     professor, assassin, judge, artist, seamstress, bonecollector) **and** `"No Ability"`
     (engineer, fisherman, huntsman, nightwatchman, preacher ×3, mezepheles); **both**
     `"Has ability"` (vigormortis, bonecollector) **and** `"Has Ability"` (banshee, pixie).
     Official is Title Case throughout: `No Ability`, `Has Ability`.
   - `GameShell.kt:461,466` places `PlacedReminder("marionette", "Is the Marionette")` but
     `characters.json` has `"Is The Marionette"`. The picker offers one string, the auto-flow
     places the other → **two tokens on the same seat**, and the
     `reminders.none { it.label == "Is the Marionette" }` guard at `GameShell.kt:460` never
     matches a hand-placed one. Straight bug, reproducible today.
   - `GameShell.kt:394,400` uses `"Is the Drunk"`; official is `"Is The Drunk"`.
     `InfoCalc.kt:*` and `GameShell.kt:366` use `"Red herring"`; official is `"Red Herring"`.
   - `StatusEffects.kt:66-69` lower-cases before matching (`"safe"`, `"protected"`,
     `"survives execution"`, `"can not die"`) so it is case-safe, but its `"can not die"`
     arm stops matching the moment the Tea Lady token is renamed to the official
     `Cannot Die`, and `"protected"` stops matching when the Innkeeper token becomes `Safe`.
     `GameActions.kt:248` (`clearEphemeral`) compares `(sourceId to label)` with
     **case-sensitive** `==`, so every rename there is load-bearing.

10. **P1 · Night order: 8 characters are misplaced, 5 are missing, 1 is present that should
    not be.** Details and corrected lists in §3.

10b. **P1 · The Hermit never wakes, for anyone.** `NightOrder.build` emits a step only when a
    seat holds that id (`NightOrder.kt:142-145`). The Hermit's official How to Run is
    *"Whenever appropriate, treat the Hermit as if they are the other Outsiders, **including
    waking them at night**, and using other Outsiders' reminder tokens. If the Hermit
    duplicates an in-play Outsider, use the Hermit's 1, 2 and 3 reminders instead."* So a
    Hermit on a script containing Butler / Klutz / Moonchild / Sweetheart / Barber / Damsel /
    Puzzlemaster / Plague Doctor / Ogre must appear at **each** of those steps. Today it
    appears at none — the `hermit` id is (correctly) in neither night list, and nothing else
    injects it. Fix: in `NightOrder.build`, treat a `hermit` seat as a holder of every
    Outsider id on the current script, and annotate the step "(Hermit)". Its `1`/`2`/`3`
    reminders exist for exactly this and are unused today.

11. **P1 · 55 of 171 characters have no guide entry of any kind** because `night_guide.json`
    only has channels for `first` and `other`. Everything a storyteller has to run during the
    **day** or at **setup** — Slayer, Artist, Fisherman, Savant, Alsaahir, Gossip, Juggler
    guesses, Klutz, Goblin, Psychopath, Boomdandy, Mastermind, Butcher, Gunslinger, Judge,
    Matron, Gangster, Bishop, Voudon, Beggar, Deviant, Scapegoat, the Drunk/Marionette setup
    swaps, Hermit, Atheist, the Djinn's special rule, and all 12 non-acting Fabled/Loric —
    has nowhere to live. Proposal in §5.2.

12. **P1 · `raw_*.json` are dead files that are now the more accurate copy.** Either delete
    them or make `characters.json` a generated artifact from them. Right now a maintainer who
    fixes one has no reason to think the other exists.

### P2

13. **P2 · 3 characters have the wrong `team`** — `bootlegger`, `gardener`, `stormcatcher`
    are officially `loric`, not `fabled`. `Team` has no `LORIC` value (`Character.kt:8-27`).

14. **P2 · 17 ability texts are stale** (§1). Most are US/UK spelling and `&`-vs-`and`, but
    six are substantive: `riot`, `gardener`, `deusexfiasco`, `fibbin`, `beggar`, and
    `bonecollector` (`at night` → `at night*` — the Bone Collector does **not** act on night
    1, and the app's own night list already agrees, so the ability text contradicts the
    app's night order).

15. **P2 · `philosopher`'s "Is the Philosopher" is a per-character reminder; officially it is
    a *global* reminder** (`remindersGlobal: ["Is The Philosopher"]`), like the Drunk's and
    the Alchemist's. Consequence: it is offered under "Philosopher" in the picker instead of
    with the other identity tokens, and the night_guide prose names it with the wrong case.

16. **P2 · 4 characters' guide prose omits an info token the official procedure requires the
    ST to show**: `exorcist.other` (**THIS CHARACTER SELECTED YOU**), `lunatic.first`
    (**THESE ARE YOUR MINIONS**, **THESE CHARACTERS ARE NOT IN PLAY**), `duchess.other`
    (**THIS CHARACTER SELECTED YOU**), `stormcatcher.first` (**STORMCAUGHT**, **THESE
    CHARACTERS ARE NOT IN PLAY**). The Lunatic omission is the one the user complained about
    ("Lunatic needs work: should have its own bluffs").

17. **P2 · `gnome` sits in the first-night order but has no night action.** Official `gnome`
    has empty `firstNightReminder`/`otherNightReminder`; the wiki's How to Run is *"During
    the day, as soon as the Gnome has entered play, mark a player of the same alignment with
    the **AMIGO** reminder."* The app invented *"Publicly announce which player is of the
    same alignment as the Gnome."* and put it after `vizier`, at the very end of night 1. It
    belongs in the `day` channel (§5.2).

### P3

18. **P3 · 110 of the 171 characters have at least one night-reminder string that differs from official** (66 firstNight, 87 otherNight). This is almost
    entirely deliberate and *better*: the app carries `bra1n/townsquare`'s verbose prose while
    the official strings are terse ("Give a finger signal."). Keep the app's, but see D16 for
    the four that lose required content, and note the official strings use `*TOKEN*` and
    `:reminder:` markup that would be worth adopting for machine-driven token placement.

19. **P3 · The dead `raw_*.json` shards are shipped to the PWA.**
    `.github/workflows/build.yml:71` does `cp engine/src/main/resources/botc/data/*.json
    site/data/`, so all four unused shards (~80 KB) are deployed and service-worker-cached
    on the user's phone alongside the three files the app actually fetches
    (`web/src/wasmJsMain/kotlin/.../Main.kt:53-56`). Fixing D12 fixes this too.

20. **P3 · `edition` id drift** — official `carousel` vs app `exp`, official `snv` vs app
    `sv`. Harmless internally (`GameData.kt:32` builds scripts from the app's ids) but it
    means official script JSON cannot be diffed against the bundle without a mapping.

---

## Proposed behaviour (spec)

### §1 — `characters.json` field corrections

Ability-text drift (all 17 confirmed against the wiki `== Summary ==` quote):

| id | field | app (stale) | official (current) |
|---|---|---|---|
| `empath` | ability | "Each night, you learn how many of your 2 alive neighbours are evil." | "Each night, you learn how many of your 2 alive neighbors are evil." |
| `beggar` | ability | "You must use a vote token to vote. Dead players may choose to give you theirs. If so, you learn their alignment. You are sober & healthy." | "You must use a vote token to vote. If a dead player gives you theirs, you learn their alignment. You are sober & healthy." |
| `tealady` | ability | "If both your alive neighbours are good, they can't die." | "If both your alive neighbors are good, they can't die." |
| `voudon` | ability | "Only you and the dead can vote. They don't need a vote token to do so. A 50% majority is not required." | "Only you & the dead can vote. They don't need a vote token to do so. A 50% majority isn't required." |
| `dreamer` | ability | "Each night, choose a player (not yourself or Travellers): you learn 1 good and 1 evil character, 1 of which is correct." | "Each night, choose a player (not yourself or Travellers): you learn 1 good & 1 evil character, 1 of which is correct." |
| `mutant` | ability | "If you are "mad" about being an Outsider, you might be executed." | "If you are “mad” about being an Outsider, you might be executed." |
| `cerenovus` | ability | "Each night, choose a player & a good character: they are "mad" they are this character tomorrow, or might be executed." | "Each night, choose a player & a good character: they are “mad” they are this character tomorrow, or might be executed." |
| `nodashii` | ability | "Each night*, choose a player: they die. Your 2 Townsfolk neighbours are poisoned." | "Each night*, choose a player: they die. Your 2 Townsfolk neighbors are poisoned." |
| `vigormortis` | ability | "Each night*, choose a player: they die. Minions you kill keep their ability & poison 1 Townsfolk neighbour. [-1 Outsider]" | "Each night*, choose a player: they die. Minions you kill keep their ability & poison 1 Townsfolk neighbor. [-1 Outsider]" |
| `barista` | ability | "Each night, until dusk, 1) a player becomes sober, healthy and gets true info, or 2) their ability works twice. They learn which." | "Each night, until dusk, 1) a player becomes sober, healthy & gets true info, or 2) their ability works twice. They learn which." |
| `bonecollector` | ability | "Once per game, at night, choose a dead player: they regain their ability until dusk." | "Once per game, at night*, choose a dead player: they regain their ability until dusk." |
| `riot` | ability | "Nominees die, but may nominate again immediately (on day 3, they must). After day 3, evil wins. [All Minions are Riot]" | "On day 3, Minions become Riot & nominees die but nominate an alive player immediately. This must happen." |
| `gangster` | ability | "Once per day, you may choose to kill an alive neighbour, if your other alive neighbour agrees." | "Once per day, you may choose to kill an alive neighbor, if your other alive neighbor agrees." |
| `fibbin` | ability | "Once per game, 1 good player might get false information." | "Once per game, 1 good player might get incorrect information." |
| `gardener` | ability | "The Storyteller assigns 1 or more players' characters." | "The Storyteller assigns all players' characters." |
| `revolutionary` | ability | "2 neighboring players are known to be the same alignment. Once per game, one of them registers falsely." | "2 neighboring players are known to be the same alignment. Once per game, 1 of them registers falsely." |
| `deusexfiasco` | ability | "Once per game, the Storyteller will make a mistake, correct it, & publicly admit to it." | "At least once per game, the Storyteller will make a mistake, correct it, and publicly admit to it." |

Setup / team / remindersGlobal / night-reminder corrections:

| id | field | app | official |
|---|---|---|---|
| `riot` | setup | `true` | `false` |
| `sentinel` | setup | `false` | `true` |
| `bootlegger` | setup | `false` | `true` |
| `gardener` | setup | `false` | `true` |
| `deusexfiasco` | setup | `false` | `true` |
| `bootlegger` | team | `fabled` | `loric` |
| `gardener` | team | `fabled` | `loric` |
| `stormcatcher` | team | `fabled` | `loric` |
| `drunk` | remindersGlobal | `["Is the Drunk"]` | `["Is The Drunk"]` |
| `philosopher` | reminders / remindersGlobal | `["Drunk","Is the Philosopher"]` / `[]` | `["Drunk"]` / `["Is The Philosopher"]` |
| `stormcatcher` | firstNightReminder | see D4 | *"Announce which character is stormcaught. If that character is in play, mark that player as \*STORMCAUGHT\*. Wake each evil player and show them the character token, then the marked player. If not in play, wake each evil player, show them the \*THESE CHARACTERS ARE NOT IN PLAY\* token & the relevant character token."* |
| `angel` | firstNightReminder | `""` (and absent from firstNight) | *"Announce which players are protected by the Angel."* |
| `buddhist` | firstNightReminder | `""` (and absent from firstNight) | *"Announce which players are affected by the Buddhist."* |
| `toymaker` | firstNightReminder | `""` (and absent from firstNight) | *"Resolve Minion Info and Demon Info, even though there are fewer than 7 players."* |
| `gnome` | firstNightReminder | *"Publicly announce which player is of the same alignment as the Gnome."* | `""` — this is a **day** action |

**Team handling for `loric`.** Two options; (a) short-term, (b) properly:
(a) keep `bootlegger`/`gardener`/`stormcatcher` as `fabled` and file the 8 new Loric as
`fabled` too — they behave like Fabled in every UI surface the app has (the Fabled sheet);
(b) add `@SerialName("loric") LORIC` to `Team` (`Character.kt:8-27`) with
`isTownResident = false`, `isEvil = false`, `displayName = "Loric"`, and give `GameShell`'s
fabled sheet a second section. Do **not** leave the raw string `"loric"` in the JSON without
one of these — `Json` throws on an unknown enum value at load.

Copy-pasteable patch (id → changed fields only; merge into `characters.json`, do not replace
whole entries — `firstNightReminder`/`otherNightReminder` are deliberately kept as the app's
verbose prose except where the table above says otherwise):

```json
{
 "empath": {
  "ability": "Each night, you learn how many of your 2 alive neighbors are evil."
 },
 "fortuneteller": {
  "reminders": [
   "Red Herring"
  ]
 },
 "slayer": {
  "reminders": [
   "No Ability"
  ]
 },
 "undertaker": {
  "reminders": [
   "Died Today"
  ]
 },
 "virgin": {
  "reminders": [
   "No Ability"
  ]
 },
 "drunk": {
  "remindersGlobal": [
   "Is The Drunk"
  ]
 },
 "scarletwoman": {
  "reminders": [
   "Is The Demon"
  ]
 },
 "beggar": {
  "ability": "You must use a vote token to vote. If a dead player gives you theirs, you learn their alignment. You are sober & healthy."
 },
 "bureaucrat": {
  "reminders": [
   "3 Votes"
  ]
 },
 "thief": {
  "reminders": [
   "Negative Vote"
  ]
 },
 "courtier": {
  "reminders": [
   "Drunk 3",
   "Drunk 2",
   "Drunk 1",
   "No Ability"
  ]
 },
 "fool": {
  "reminders": [
   "No Ability"
  ]
 },
 "innkeeper": {
  "reminders": [
   "Safe",
   "Safe",
   "Drunk"
  ]
 },
 "minstrel": {
  "reminders": [
   "Everyone Is Drunk"
  ]
 },
 "professor": {
  "reminders": [
   "Alive",
   "No Ability"
  ]
 },
 "tealady": {
  "ability": "If both your alive neighbors are good, they can't die.",
  "reminders": [
   "Cannot Die",
   "Cannot Die"
  ]
 },
 "lunatic": {
  "reminders": [
   "Chosen",
   "Chosen",
   "Chosen"
  ]
 },
 "assassin": {
  "reminders": [
   "Dead",
   "No Ability"
  ]
 },
 "devilsadvocate": {
  "reminders": [
   "Survives Execution"
  ]
 },
 "godfather": {
  "reminders": [
   "Died Today",
   "Dead"
  ]
 },
 "po": {
  "reminders": [
   "Dead",
   "Dead",
   "Dead",
   "3 Attacks"
  ]
 },
 "pukka": {
  "reminders": [
   "Poisoned",
   "Poisoned",
   "Dead"
  ]
 },
 "shabaloth": {
  "reminders": [
   "Dead",
   "Dead",
   "Alive"
  ]
 },
 "zombuul": {
  "reminders": [
   "Died Today",
   "Dead"
  ]
 },
 "apprentice": {
  "reminders": [
   "Is The Apprentice"
  ]
 },
 "bishop": {
  "reminders": [
   "Nominate Good",
   "Nominate Evil"
  ]
 },
 "judge": {
  "reminders": [
   "No Ability"
  ]
 },
 "voudon": {
  "ability": "Only you & the dead can vote. They don't need a vote token to do so. A 50% majority isn't required."
 },
 "artist": {
  "reminders": [
   "No Ability"
  ]
 },
 "dreamer": {
  "ability": "Each night, choose a player (not yourself or Travellers): you learn 1 good & 1 evil character, 1 of which is correct."
 },
 "flowergirl": {
  "reminders": [
   "Demon Voted",
   "Demon Not Voted"
  ]
 },
 "juggler": {
  "reminders": [
   "Correct",
   "Correct",
   "Correct",
   "Correct",
   "Correct"
  ]
 },
 "mathematician": {
  "reminders": [
   "Abnormal",
   "Abnormal",
   "Abnormal",
   "Abnormal",
   "Abnormal"
  ]
 },
 "philosopher": {
  "reminders": [
   "Drunk"
  ],
  "remindersGlobal": [
   "Is The Philosopher"
  ]
 },
 "seamstress": {
  "reminders": [
   "No Ability"
  ]
 },
 "towncrier": {
  "reminders": [
   "Minions Not Nominated",
   "Minion Nominated"
  ]
 },
 "barber": {
  "reminders": [
   "Haircuts Tonight"
  ]
 },
 "mutant": {
  "ability": "If you are “mad” about being an Outsider, you might be executed."
 },
 "cerenovus": {
  "ability": "Each night, choose a player & a good character: they are “mad” they are this character tomorrow, or might be executed."
 },
 "nodashii": {
  "ability": "Each night*, choose a player: they die. Your 2 Townsfolk neighbors are poisoned.",
  "reminders": [
   "Dead",
   "Poisoned",
   "Poisoned"
  ]
 },
 "vigormortis": {
  "ability": "Each night*, choose a player: they die. Minions you kill keep their ability & poison 1 Townsfolk neighbor. [-1 Outsider]",
  "reminders": [
   "Dead",
   "Has Ability",
   "Has Ability",
   "Has Ability",
   "Poisoned",
   "Poisoned",
   "Poisoned"
  ]
 },
 "barista": {
  "ability": "Each night, until dusk, 1) a player becomes sober, healthy & gets true info, or 2) their ability works twice. They learn which.",
  "reminders": [
   "Sober & Healthy",
   "Acts Twice",
   "?",
   "?"
  ]
 },
 "bonecollector": {
  "ability": "Once per game, at night*, choose a dead player: they regain their ability until dusk.",
  "reminders": [
   "No Ability",
   "Has Ability"
  ]
 },
 "harlot": {
  "reminders": [
   "Dead",
   "Dead"
  ]
 },
 "riot": {
  "ability": "On day 3, Minions become Riot & nominees die but nominate an alive player immediately. This must happen.",
  "setup": false
 },
 "gangster": {
  "ability": "Once per day, you may choose to kill an alive neighbor, if your other alive neighbor agrees."
 },
 "angel": {
  "reminders": [
   "Protected",
   "Protected",
   "Something Bad"
  ]
 },
 "bootlegger": {
  "setup": true,
  "team": "loric",
  "reminders": [
   "?",
   "?"
  ]
 },
 "duchess": {
  "reminders": [
   "Visitor",
   "Visitor",
   "False Info"
  ]
 },
 "fibbin": {
  "ability": "Once per game, 1 good player might get incorrect information.",
  "reminders": [
   "No Ability"
  ]
 },
 "gardener": {
  "ability": "The Storyteller assigns all players' characters.",
  "setup": true,
  "team": "loric"
 },
 "revolutionary": {
  "ability": "2 neighboring players are known to be the same alignment. Once per game, 1 of them registers falsely.",
  "reminders": [
   "Register Falsely?",
   "Aligned",
   "Aligned"
  ]
 },
 "sentinel": {
  "setup": true
 },
 "spiritofivory": {
  "reminders": [
   "No More Evil"
  ]
 },
 "stormcatcher": {
  "team": "loric",
  "reminders": [
   "Stormcaught"
  ]
 },
 "deusexfiasco": {
  "ability": "At least once per game, the Storyteller will make a mistake, correct it, and publicly admit to it.",
  "setup": true,
  "reminders": [
   "Whoopsie"
  ]
 }
}
```

### §2 — Characters added since the bundle (10)

Eight `loric`, one `minion` (Wraith, revealed 2025-07-03), one `traveller` (Cacklejack).
`edition` is mapped to the app's ids (`carousel` → `exp`); `team` uses the app's `"traveler"`
spelling. **`"loric"` requires the `Team` change described in §1.** Wraith additionally
needs a night step — it is in *both* official night lists.

```json
[
 {
  "id": "wraith",
  "name": "Wraith",
  "edition": "exp",
  "team": "minion",
  "ability": "You may choose to open your eyes at night. You wake when other evil players do.",
  "setup": false,
  "firstNightReminder": "Wake the Wraith whenever other evil players wake.",
  "otherNightReminder": "Wake the Wraith whenever other evil players wake.",
  "reminders": [],
  "remindersGlobal": []
 },
 {
  "id": "cacklejack",
  "name": "Cacklejack",
  "edition": "exp",
  "team": "traveler",
  "ability": "Each day, choose a player: a different player changes character tonight.",
  "setup": false,
  "firstNightReminder": "",
  "otherNightReminder": "Before dawn, choose a player not marked *NOT ME*. Wake the target. Show the *YOU ARE* info token & their new character token.",
  "reminders": [
   "Not Me"
  ],
  "remindersGlobal": []
 },
 {
  "id": "bigwig",
  "name": "Big Wig",
  "edition": "loric",
  "team": "loric",
  "ability": "Each nominee chooses a player: until voting, only they may speak & they are mad the nominee is good or they might die.",
  "setup": false,
  "firstNightReminder": "",
  "otherNightReminder": "",
  "reminders": [],
  "remindersGlobal": []
 },
 {
  "id": "godofug",
  "name": "God of Ug",
  "edition": "loric",
  "team": "loric",
  "ability": "One Ug hat. When wear Ug hat, must speak one sound at a time but vote twice. If fail, pass Ug hat.",
  "setup": false,
  "firstNightReminder": "",
  "otherNightReminder": "",
  "reminders": [
   "Hat"
  ],
  "remindersGlobal": []
 },
 {
  "id": "hindu",
  "name": "Hindu",
  "edition": "loric",
  "team": "loric",
  "ability": "The first 4 players to die are immediately reincarnated as Travellers of the same alignment.",
  "setup": false,
  "firstNightReminder": "",
  "otherNightReminder": "",
  "reminders": [],
  "remindersGlobal": []
 },
 {
  "id": "knaves",
  "name": "Knaves",
  "edition": "loric",
  "team": "loric",
  "ability": "There are 2 Storytellers: one lies & one tells the truth. Once per game, at dusk, they might switch.",
  "setup": false,
  "firstNightReminder": "",
  "otherNightReminder": "",
  "reminders": [],
  "remindersGlobal": []
 },
 {
  "id": "pope",
  "name": "Pope",
  "edition": "loric",
  "team": "loric",
  "ability": "There are duplicate good characters in play. They might also be bluffs.",
  "setup": true,
  "firstNightReminder": "",
  "otherNightReminder": "",
  "reminders": [],
  "remindersGlobal": []
 },
 {
  "id": "tor",
  "name": "Tor",
  "edition": "loric",
  "team": "loric",
  "ability": "Players don't know their character or alignment. They learn them when they die.",
  "setup": true,
  "firstNightReminder": "Skip Minion Info and Demon Info.",
  "otherNightReminder": "If a player died tonight, show the *YOU ARE* info token, their character token, & give a thumb signal.",
  "reminders": [],
  "remindersGlobal": []
 },
 {
  "id": "ventriloquist",
  "name": "Ventriloquist",
  "edition": "loric",
  "team": "loric",
  "ability": "If a player is mad as a fresh character during their nomination, they might not die if executed today.",
  "setup": false,
  "firstNightReminder": "",
  "otherNightReminder": "",
  "reminders": [
   "Mad"
  ],
  "remindersGlobal": []
 },
 {
  "id": "zenomancer",
  "name": "Zenomancer",
  "edition": "loric",
  "team": "loric",
  "ability": "One or more players each have a goal. When achieved, that player learns a piece of true info.",
  "setup": false,
  "firstNightReminder": "",
  "otherNightReminder": "",
  "reminders": [
   "Goal",
   "Goal",
   "Goal"
  ],
  "remindersGlobal": []
 }
]
```

Official `special` blocks worth carrying (the app has no field for these yet, but they are
what an implementer needs):

| id | special |
|---|---|
| `wraith` | `{"type":"player","name":"open-eyes","time":"night"}` — the Wraith may open their eyes; wake them alongside every other evil wake |
| `godofug` | `{"type":"vote","name":"multiplier","value":2}` + `{"type":"reminder","name":"public"}` |
| `pope` | `{"type":"selection","name":"good-duplicate"}` + `{"type":"selection","name":"evil-duplicate"}` |
| `gardener` | `{"name":"distribute-roles","type":"ability","time":"pregame"}` |
| `tor` | none, but `setup: true`, and its first-night step is *"Skip Minion Info and Demon Info."* |

For reference, the official `special` blocks the app **already** needs and does not model:
`villageidiot` / `legion` `bag-duplicate`; `drunk` / `marionette` / `lilmonsta` `bag-disabled`
+ `replace-character`; `spy` / `widow` / `apprentice` `signal:grimoire@night`;
`organgrinder` `vote:hidden`; `thief` `vote:multiplier -1`; `bureaucrat` `vote:multiplier 3`;
`boomdandy` / `lilmonsta` `ability:pointing`; `atheist` `good-duplicate`+`evil-duplicate`;
`ferryman` `ghost-votes`; `fiddler` `pointing@day`.

### §3 — Night order

Diff computed against `nightsheet.json` with the app's markers normalised
(`DUSK`↔`dusk`, `MINION_INFO`↔`minioninfo`, `DEMON_INFO`↔`demoninfo`, `DAWN`↔`dawn`).

**firstNight — official 80 entries, app 76.**

| # | problem |
|---|---|
| 1 | `angel` missing — official position **2** (immediately after `dusk`) |
| 2 | `buddhist` missing — official position **3** |
| 3 | `toymaker` missing — official position **4** |
| 4 | `wraith` missing — official position **6** (character absent from the dataset entirely) |
| 5 | `tor` missing — official position **19**, between `magician` and `minioninfo` (character absent) |
| 6 | `stormcatcher` **misplaced**: app has it at index 8 (between `boffin` and `philosopher`); official position is **5**, *before* `lordoftyphon` — it must resolve before the Demon is placed, because it can force a character into play |
| 7 | `gnome` **should not be here**: app has it at the very end, after `vizier`; official `gnome` has no night action at all (D17) |

Everything else in firstNight is in the correct relative order, including
`DUSK(1) … MINION_INFO(20) → snitch → lunatic → summoner → DEMON_INFO(24) … DAWN(78) →
leviathan(79) → vizier(80)`. Note the two entries **after DAWN** — `leviathan` and `vizier`
are announcements made in daylight, and the app already models that correctly.

**otherNight — official 99 entries, app 96.**

| # | problem |
|---|---|
| 1 | `duchess` **misplaced**: app index 79 (between `towncrier` and `oracle`); official position **2**, immediately after `dusk` — the Duchess's visitors are woken *first*, before anything can change their info |
| 2 | `toymaker` **misplaced**: app index 34 (after `lycanthrope`); official position **3** |
| 3 | `wraith` missing — official position **4** (character absent) |
| 4 | `cacklejack` missing — official position **5** (character absent) |
| 5 | `bonecollector` **misplaced**: app index 5 (before `harlot`); official position **10**, *after* `harlot` |
| 6 | `plaguedoctor` **badly misplaced**: app index 4 (5th step of the night); official position **62**, between `sweetheart` and `sage`. Running it 58 steps early hands out the Storyteller-gained Minion ability before the Minions have acted. |
| 7 | `innkeeper` **misplaced**: app index 14 (before `courtier`); official position **19**, *after* `courtier` |
| 8 | `tor` missing — official position **74** (character absent) |
| 9 | `riot` **badly misplaced**: app index 53 (with the Demons, after `kazali`); official position **97**, immediately before `dawn` |
| 10 | `leviathan` **badly misplaced**: app index 54 (with the Demons); official position **99**, *after* `dawn` |

Neither `hermit` nor `ogre` nor `widow` is actually missing: `widow` and `ogre` are both
present in firstNight in both datasets and act only on night 1, and official `hermit` has
**no** night reminder in either list, because its Outsider abilities are run under whichever
Outsider's own step applies — which the app never does (D10b). `hermit` also needs a
**setup** guide entry for the `-0 or -1 Outsider` choice (§5.2).

Cross-check performed: in both datasets, every character with a non-empty
`firstNightReminder` is in the corresponding first-night list and vice versa (same for
other nights) — the two files are internally consistent, they are just consistent with the
wrong data.

Corrected lists, ready to paste over `night_and_jinxes.json`'s `firstNight` / `otherNight`
(entries for characters not yet in `characters.json` are harmless — `NightOrder.build` skips
any id `lookup()` cannot resolve, `NightOrder.kt:142`):

```json
{
 "firstNight": [
  "DUSK",
  "angel",
  "buddhist",
  "toymaker",
  "stormcatcher",
  "wraith",
  "lordoftyphon",
  "kazali",
  "apprentice",
  "barista",
  "bureaucrat",
  "thief",
  "boffin",
  "philosopher",
  "alchemist",
  "poppygrower",
  "yaggababble",
  "magician",
  "tor",
  "MINION_INFO",
  "snitch",
  "lunatic",
  "summoner",
  "DEMON_INFO",
  "king",
  "sailor",
  "marionette",
  "engineer",
  "preacher",
  "lilmonsta",
  "lleech",
  "xaan",
  "poisoner",
  "widow",
  "courtier",
  "wizard",
  "snakecharmer",
  "godfather",
  "organgrinder",
  "devilsadvocate",
  "eviltwin",
  "witch",
  "cerenovus",
  "fearmonger",
  "harpy",
  "mezepheles",
  "pukka",
  "pixie",
  "huntsman",
  "damsel",
  "amnesiac",
  "washerwoman",
  "librarian",
  "investigator",
  "chef",
  "empath",
  "fortuneteller",
  "butler",
  "grandmother",
  "clockmaker",
  "dreamer",
  "seamstress",
  "steward",
  "knight",
  "noble",
  "balloonist",
  "shugenja",
  "villageidiot",
  "bountyhunter",
  "nightwatchman",
  "cultleader",
  "spy",
  "ogre",
  "highpriestess",
  "general",
  "chambermaid",
  "mathematician",
  "DAWN",
  "leviathan",
  "vizier"
 ],
 "otherNight": [
  "DUSK",
  "duchess",
  "toymaker",
  "wraith",
  "cacklejack",
  "barista",
  "bureaucrat",
  "thief",
  "harlot",
  "bonecollector",
  "philosopher",
  "poppygrower",
  "sailor",
  "engineer",
  "preacher",
  "xaan",
  "poisoner",
  "courtier",
  "innkeeper",
  "wizard",
  "gambler",
  "acrobat",
  "snakecharmer",
  "monk",
  "organgrinder",
  "devilsadvocate",
  "witch",
  "cerenovus",
  "pithag",
  "fearmonger",
  "harpy",
  "mezepheles",
  "scarletwoman",
  "summoner",
  "lunatic",
  "exorcist",
  "lycanthrope",
  "princess",
  "legion",
  "imp",
  "zombuul",
  "pukka",
  "shabaloth",
  "po",
  "fanggu",
  "nodashii",
  "vortox",
  "lordoftyphon",
  "vigormortis",
  "ojo",
  "alhadikhia",
  "lleech",
  "lilmonsta",
  "yaggababble",
  "kazali",
  "assassin",
  "godfather",
  "gossip",
  "hatter",
  "barber",
  "sweetheart",
  "plaguedoctor",
  "sage",
  "banshee",
  "professor",
  "choirboy",
  "huntsman",
  "damsel",
  "amnesiac",
  "farmer",
  "tinker",
  "moonchild",
  "grandmother",
  "tor",
  "ravenkeeper",
  "empath",
  "fortuneteller",
  "undertaker",
  "dreamer",
  "flowergirl",
  "towncrier",
  "oracle",
  "seamstress",
  "juggler",
  "balloonist",
  "villageidiot",
  "king",
  "bountyhunter",
  "nightwatchman",
  "cultleader",
  "butler",
  "spy",
  "highpriestess",
  "general",
  "chambermaid",
  "mathematician",
  "riot",
  "DAWN",
  "leviathan"
 ]
}
```

### §4 — Jinxes: the complete current list (131)

Replaces `night_and_jinxes.json`'s `"jinxes"` array wholesale. Flattened from the official
grouped form into the app's `{id1, id2, reason}`, with `id1` = the group owner. **80 of these
are new to the app, 39 replace stale text, and the 7 bundled jinxes not present here have
been retired officially** (`kazali+choirboy`, `marionette+damsel`, `lycanthrope+gambler`,
`marionette+poppygrower`, `marionette+snitch`, `summoner+poisoner`, `riot+saint`) — the
Marionette ones were folded into the single official *"If there would be a Marionette in
play, they enter play after the Demon & must start as their neighbor"* jinx, and
`summoner+poisoner` into *"If the living Summoner has no ability, the Storyteller has the
Summoner ability."*

`GameData.activeJinxes` (`GameData.kt:23-25`) filters on "both ids in the script", so it is
already order-insensitive and needs no change. But note that official `id1` is the *owner* of
the jinx group and the bundled list picked the opposite order for **21** pairs
(`chambermaid+mathematician`, `lunatic+mathematician`, `baron/godfather/spy/widow/pithag/
lleech + heretic`, `summoner+marionette`, `magician/poppygrower/scarletwoman + lilmonsta`,
`spy+magician`, `widow+magician`, `philosopher+bountyhunter`, `fanggu+scarletwoman`,
`alhadikhia+scarletwoman`, `summoner+alchemist`, `kazali+bountyhunter`, `kazali+marionette`,
`exorcist+yaggababble`), so anything that renders or sorts by `id1` will reorder,
and any future "does this exact pair have a jinx?" helper must normalise the pair.

```json
[
 {
  "id1": "alchemist",
  "id2": "boffin",
  "reason": "If the Alchemist has the Boffin ability, the Alchemist does not learn what ability the Demon has."
 },
 {
  "id1": "alchemist",
  "id2": "marionette",
  "reason": "An Alchemist-Marionette has no Marionette ability & the Marionette is in play."
 },
 {
  "id1": "alchemist",
  "id2": "mastermind",
  "reason": "An Alchemist-Mastermind has no Mastermind ability & the Mastermind is not-in-play."
 },
 {
  "id1": "alchemist",
  "id2": "organgrinder",
  "reason": "If the Alchemist has the Organ Grinder ability, the Organ Grinder is in play. If both are sober, both are drunk."
 },
 {
  "id1": "alchemist",
  "id2": "spy",
  "reason": "An Alchemist-Spy has no Spy ability & a Spy is in play. After each execution, a living Alchemist-Spy may publicly guess a living player as the Spy. If correct, the Demon must choose the Spy tonight."
 },
 {
  "id1": "alchemist",
  "id2": "summoner",
  "reason": "The Alchemist-Summoner does not get bluffs, and chooses which Demon but not which player. If they die before this happens, evil wins. [No Demon]"
 },
 {
  "id1": "alchemist",
  "id2": "widow",
  "reason": "An Alchemist-Widow has no Widow ability & a Widow is in play. After each execution, a living Alchemist-Widow may publicly guess a living player as the Widow. If correct, the Demon must choose the Widow tonight."
 },
 {
  "id1": "alchemist",
  "id2": "wraith",
  "reason": "An Alchemist-Wraith has no Wraith ability & a Wraith is in play. After each execution, a living Alchemist-Wraith may publicly guess a living player as the Wraith. If correct, the Demon must choose the Wraith tonight."
 },
 {
  "id1": "alhadikhia",
  "id2": "mastermind",
  "reason": "If the Al-Hadikhia dies by execution, and the Mastermind is alive, the Al-Hadikhia chooses 3 good players tonight: if all 3 choose to live, evil wins. Otherwise, good wins."
 },
 {
  "id1": "alhadikhia",
  "id2": "princess",
  "reason": "If the Princess nominated & executed a player on their 1st day, no one dies to the Al-Hadikhia tonight."
 },
 {
  "id1": "boffin",
  "id2": "cultleader",
  "reason": "If the Demon has the Cult Leader ability, they can’t turn good due to this ability."
 },
 {
  "id1": "boffin",
  "id2": "drunk",
  "reason": "The Demon cannot have the Drunk ability."
 },
 {
  "id1": "boffin",
  "id2": "goon",
  "reason": "If the Demon has the Goon ability, they can’t turn good due to this ability."
 },
 {
  "id1": "boffin",
  "id2": "heretic",
  "reason": "The Demon cannot have the Heretic ability."
 },
 {
  "id1": "boffin",
  "id2": "ogre",
  "reason": "The Demon cannot have the Ogre ability."
 },
 {
  "id1": "boffin",
  "id2": "politician",
  "reason": "The Demon cannot have the Politician ability."
 },
 {
  "id1": "boffin",
  "id2": "villageidiot",
  "reason": "If there is a spare token, the Boffin can give the Demon the Village Idiot ability."
 },
 {
  "id1": "bountyhunter",
  "id2": "kazali",
  "reason": "If the Kazali turns the Bounty Hunter into a Minion, an evil Townsfolk is not created."
 },
 {
  "id1": "bountyhunter",
  "id2": "philosopher",
  "reason": "If the Philosopher gains the Bounty Hunter ability, a Townsfolk might turn evil."
 },
 {
  "id1": "butler",
  "id2": "organgrinder",
  "reason": "If the Organ Grinder is causing eyes closed voting, the Butler may raise their hand to vote but their vote is only counted if their master voted too."
 },
 {
  "id1": "cannibal",
  "id2": "butler",
  "reason": "If the Cannibal gains the Butler ability, the Cannibal learns this."
 },
 {
  "id1": "cannibal",
  "id2": "juggler",
  "reason": "If the Juggler guesses on their first day and dies by execution, tonight the living Cannibal learns how many guesses the Juggler got correct."
 },
 {
  "id1": "cannibal",
  "id2": "princess",
  "reason": "If the Cannibal nominated, executed, & killed the Princess today, the Demon doesn’t kill tonight."
 },
 {
  "id1": "cannibal",
  "id2": "zealot",
  "reason": "If the Cannibal gains the Zealot ability, the Cannibal learns this."
 },
 {
  "id1": "cerenovus",
  "id2": "goblin",
  "reason": "The Cerenovus may choose to make a player mad that they are the Goblin."
 },
 {
  "id1": "heretic",
  "id2": "baron",
  "reason": "Only 1 jinxed character can be in play."
 },
 {
  "id1": "heretic",
  "id2": "godfather",
  "reason": "Only 1 jinxed character can be in play."
 },
 {
  "id1": "heretic",
  "id2": "lleech",
  "reason": "Only 1 jinxed character can be in play."
 },
 {
  "id1": "heretic",
  "id2": "pithag",
  "reason": "Only 1 jinxed character can be in play."
 },
 {
  "id1": "heretic",
  "id2": "spy",
  "reason": "Only 1 jinxed character can be in play."
 },
 {
  "id1": "heretic",
  "id2": "widow",
  "reason": "Only 1 jinxed character can be in play."
 },
 {
  "id1": "legion",
  "id2": "engineer",
  "reason": "If Legion is created, all evil players become Legion. If Legion is in play, the Engineer starts knowing this but has no ability."
 },
 {
  "id1": "legion",
  "id2": "hatter",
  "reason": "If Legion is created, all evil players become Legion. If Legion is in play, the Hatter has no ability."
 },
 {
  "id1": "legion",
  "id2": "minstrel",
  "reason": "If Legion died by execution today, Legion keeps their ability, but the Minstrel might learn they are Legion."
 },
 {
  "id1": "legion",
  "id2": "politician",
  "reason": "The Politician might register as evil to Legion."
 },
 {
  "id1": "legion",
  "id2": "preacher",
  "reason": "If the Preacher chooses Legion, Legion keeps their ability, but the Preacher might learn they are Legion."
 },
 {
  "id1": "legion",
  "id2": "summoner",
  "reason": "If Legion is summoned, all evil players become Legion."
 },
 {
  "id1": "legion",
  "id2": "zealot",
  "reason": "The Zealot might register as evil to Legion."
 },
 {
  "id1": "leviathan",
  "id2": "banshee",
  "reason": "Each night*, the Leviathan chooses an alive good player (different to previous nights): a chosen Banshee dies & gains their ability."
 },
 {
  "id1": "leviathan",
  "id2": "exorcist",
  "reason": "If the Leviathan nominates and executes the Exorcist-chosen player, good wins."
 },
 {
  "id1": "leviathan",
  "id2": "farmer",
  "reason": "Each night*, the Leviathan chooses an alive good player (different to previous nights): a chosen Farmer uses their ability but does not die."
 },
 {
  "id1": "leviathan",
  "id2": "grandmother",
  "reason": "If the Leviathan is in play and the Grandchild dies by execution, evil wins."
 },
 {
  "id1": "leviathan",
  "id2": "hatter",
  "reason": "The Leviathan cannot enter play after day 5."
 },
 {
  "id1": "leviathan",
  "id2": "innkeeper",
  "reason": "If the Leviathan nominates and executes an Innkeeper-protected player, good wins."
 },
 {
  "id1": "leviathan",
  "id2": "king",
  "reason": "If the Leviathan is in play, and at least 1 player is dead, the King learns an alive character each night."
 },
 {
  "id1": "leviathan",
  "id2": "mayor",
  "reason": "If the Leviathan and the Mayor are alive on day 5 & no execution occurs, good wins."
 },
 {
  "id1": "leviathan",
  "id2": "monk",
  "reason": "If the Leviathan nominates and executes the Monk-protected player, good wins."
 },
 {
  "id1": "leviathan",
  "id2": "pithag",
  "reason": "The Leviathan cannot enter play after day 5."
 },
 {
  "id1": "leviathan",
  "id2": "ravenkeeper",
  "reason": "Each night*, the Leviathan chooses an alive player (different to previous nights): a chosen Ravenkeeper uses their ability but does not die."
 },
 {
  "id1": "leviathan",
  "id2": "sage",
  "reason": "Each night*, the Leviathan chooses an alive good player (different to previous nights): a chosen Sage uses their ability but does not die."
 },
 {
  "id1": "leviathan",
  "id2": "soldier",
  "reason": "If the Leviathan nominates and executes the Soldier, good wins."
 },
 {
  "id1": "lilmonsta",
  "id2": "hatter",
  "reason": "If the Hatter dies & the Demon chooses Lil' Monsta, they also choose a Minion to become."
 },
 {
  "id1": "lilmonsta",
  "id2": "magician",
  "reason": "If the Magician is alive, the Storyteller chooses which Minion babysits Lil' Monsta."
 },
 {
  "id1": "lilmonsta",
  "id2": "poppygrower",
  "reason": "If Lil' Monsta & the Poppy Grower are alive, Minions wake one by one, until one of them chooses to take the Lil' Monsta token."
 },
 {
  "id1": "lilmonsta",
  "id2": "psychopath",
  "reason": "If the Psychopath is babysitting Lil' Monsta, they die when executed."
 },
 {
  "id1": "lilmonsta",
  "id2": "scarletwoman",
  "reason": "If Lil' Monsta dies with 5 or more players alive, the Scarlet Woman babysits Lil' Monsta for the rest of the game."
 },
 {
  "id1": "lilmonsta",
  "id2": "vizier",
  "reason": "If the Vizier is babysitting Lil' Monsta, they die when executed."
 },
 {
  "id1": "lleech",
  "id2": "mastermind",
  "reason": "If the Mastermind is alive and the Lleech host dies by execution, the Lleech lives but loses their ability."
 },
 {
  "id1": "lleech",
  "id2": "slayer",
  "reason": "If the Slayer slays the Lleech host, the host dies."
 },
 {
  "id1": "magician",
  "id2": "legion",
  "reason": "If the Magician is in play, during the Demon info step, Legion wake in separate groups. Each group learns which players are good, but does not learn the Magician."
 },
 {
  "id1": "magician",
  "id2": "marionette",
  "reason": "If the Magician is alive, the Demon doesn't know which neighbor is the Marionette."
 },
 {
  "id1": "magician",
  "id2": "spy",
  "reason": "When the Spy sees the Grimoire, the Demon and Magician's character tokens are removed."
 },
 {
  "id1": "magician",
  "id2": "vizier",
  "reason": "If the Vizier is in play, the Magician has no ability but is immune to the Vizier's ability."
 },
 {
  "id1": "magician",
  "id2": "widow",
  "reason": "When the Widow sees the Grimoire, the Demon and Magician's character tokens are removed."
 },
 {
  "id1": "magician",
  "id2": "wraith",
  "reason": "After each execution, the living Magician may publicly guess a living player as the Wraith. If correct, the Demon must choose the Wraith tonight."
 },
 {
  "id1": "marionette",
  "id2": "balloonist",
  "reason": "If the Marionette thinks that they are the Balloonist, an Outsider might have been added during setup."
 },
 {
  "id1": "marionette",
  "id2": "huntsman",
  "reason": "If the Marionette thinks that they are the Huntsman, the Damsel was added during setup."
 },
 {
  "id1": "marionette",
  "id2": "kazali",
  "reason": "If there would be a Marionette in play, they enter play after the Demon & must start as their neighbor."
 },
 {
  "id1": "marionette",
  "id2": "lilmonsta",
  "reason": "If there would be a Marionette in play, they enter play after the Demon & must start as their neighbor."
 },
 {
  "id1": "marionette",
  "id2": "summoner",
  "reason": "If there would be a Marionette in play, they enter play after the Demon & must start as their neighbor."
 },
 {
  "id1": "mastermind",
  "id2": "vigormortis",
  "reason": "A Mastermind that has their ability keeps it if the Vigormortis dies."
 },
 {
  "id1": "mathematician",
  "id2": "chambermaid",
  "reason": "The Chambermaid can detect if the Mathematician will wake tonight."
 },
 {
  "id1": "mathematician",
  "id2": "drunk",
  "reason": "The Mathematician learns if the Drunk’s ability yielded false info or failed to work properly."
 },
 {
  "id1": "mathematician",
  "id2": "lunatic",
  "reason": "The Mathematician learns if the Lunatic attacks a different player than the real Demon attacked."
 },
 {
  "id1": "mathematician",
  "id2": "marionette",
  "reason": "The Mathematician learns if the Marionette’s ability yielded false info or failed to work properly."
 },
 {
  "id1": "pithag",
  "id2": "cultleader",
  "reason": "If the Pit-Hag turns an evil player into the Cult Leader, they can't turn good due to their own ability."
 },
 {
  "id1": "pithag",
  "id2": "damsel",
  "reason": "If a Pit-Hag creates a Damsel, the Storyteller chooses which player it is."
 },
 {
  "id1": "pithag",
  "id2": "goon",
  "reason": "If the Pit-Hag turns an evil player into the Goon, they can't turn good due to their own ability."
 },
 {
  "id1": "pithag",
  "id2": "ogre",
  "reason": "If the Pit-Hag turns an evil player into the Ogre, they can't turn good due to their own ability."
 },
 {
  "id1": "pithag",
  "id2": "politician",
  "reason": "If the Pit-Hag turns an evil player into the Politician, they can't turn good due to their own ability."
 },
 {
  "id1": "pithag",
  "id2": "villageidiot",
  "reason": "If there is a spare token, the Pit-Hag can create an extra Village Idiot. If so, the drunk Village Idiot might change."
 },
 {
  "id1": "plaguedoctor",
  "id2": "baron",
  "reason": "If the Storyteller would gain the Baron ability, up to two players become Outsiders."
 },
 {
  "id1": "plaguedoctor",
  "id2": "boomdandy",
  "reason": "If the Storyteller would gain the Boomdandy ability, a player becomes the Boomdandy."
 },
 {
  "id1": "plaguedoctor",
  "id2": "eviltwin",
  "reason": "If the Storyteller would gain the Evil Twin ability, a player becomes the Evil Twin."
 },
 {
  "id1": "plaguedoctor",
  "id2": "fearmonger",
  "reason": "If the Storyteller would gain the Fearmonger ability, a Minion gains it, and learns this."
 },
 {
  "id1": "plaguedoctor",
  "id2": "goblin",
  "reason": "If the Storyteller would gain the Goblin ability, a Minion gains it, and learns this."
 },
 {
  "id1": "plaguedoctor",
  "id2": "marionette",
  "reason": "If the Storyteller would gain the Marionette ability, one of the Demon's good neighbors becomes the Marionette."
 },
 {
  "id1": "plaguedoctor",
  "id2": "scarletwoman",
  "reason": "If the Storyteller would gain the Scarlet Woman ability, a Minion gains it, and learns this."
 },
 {
  "id1": "plaguedoctor",
  "id2": "spy",
  "reason": "If the Storyteller would gain the Spy ability, a Minion gains it, and learns this."
 },
 {
  "id1": "plaguedoctor",
  "id2": "wraith",
  "reason": "If the Storyteller would gain the Wraith ability, a Minion gains it, and learns this."
 },
 {
  "id1": "recluse",
  "id2": "ogre",
  "reason": "If the Recluse registers as evil to the Ogre, the Ogre learns that they are evil."
 },
 {
  "id1": "recluse",
  "id2": "sage",
  "reason": "The Recluse might register as the Demon to the Sage."
 },
 {
  "id1": "riot",
  "id2": "atheist",
  "reason": "During a riot, if the Storyteller is nominated, players vote. If they are \"about to die\", the game ends. If not, they nominate again."
 },
 {
  "id1": "riot",
  "id2": "banshee",
  "reason": "Each night*, Riot chooses an alive good player (different to previous nights): a chosen Banshee dies & gains their ability."
 },
 {
  "id1": "riot",
  "id2": "exorcist",
  "reason": "If Riot nominates and executes the Exorcist-chosen player, good wins."
 },
 {
  "id1": "riot",
  "id2": "farmer",
  "reason": "Each night*, Riot chooses an alive good player (different to previous nights): a chosen Farmer uses their ability but does not die."
 },
 {
  "id1": "riot",
  "id2": "grandmother",
  "reason": "If Riot is in play and the Grandchild dies by execution, evil wins."
 },
 {
  "id1": "riot",
  "id2": "innkeeper",
  "reason": "If Riot nominates and executes an Innkeeper-protected player, good wins."
 },
 {
  "id1": "riot",
  "id2": "king",
  "reason": "If Riot is in play, and at least 1 player is dead, the King learns an alive character each night."
 },
 {
  "id1": "riot",
  "id2": "mayor",
  "reason": "The Mayor may choose to stop the riot. If they do so when only 1 Riot is alive, good wins. Otherwise, evil wins."
 },
 {
  "id1": "riot",
  "id2": "monk",
  "reason": "If Riot nominates and executes the Monk-protected player, good wins."
 },
 {
  "id1": "riot",
  "id2": "ravenkeeper",
  "reason": "Each night*, Riot chooses an alive good player (different to previous nights): a chosen Ravenkeeper uses their ability but does not die."
 },
 {
  "id1": "riot",
  "id2": "sage",
  "reason": "Each night*, Riot chooses an alive good player (different to previous nights): a chosen Sage uses their ability but does not die."
 },
 {
  "id1": "riot",
  "id2": "soldier",
  "reason": "If Riot nominates and executes the Soldier, good wins."
 },
 {
  "id1": "scarletwoman",
  "id2": "alhadikhia",
  "reason": "If there would be two Demons, one of which was the Scarlet Woman, the Scarlet Woman becomes the Scarlet Woman again."
 },
 {
  "id1": "scarletwoman",
  "id2": "fanggu",
  "reason": "If there would be two Demons, one of which was the Scarlet Woman, the Scarlet Woman remains the Scarlet Woman."
 },
 {
  "id1": "spy",
  "id2": "damsel",
  "reason": "If the Spy is (or has been) in play, the Damsel is poisoned."
 },
 {
  "id1": "spy",
  "id2": "ogre",
  "reason": "The Spy registers as evil to the Ogre."
 },
 {
  "id1": "spy",
  "id2": "poppygrower",
  "reason": "If the Poppy Grower has their ability, the Spy does not see the Grimoire."
 },
 {
  "id1": "summoner",
  "id2": "clockmaker",
  "reason": "The Summoner registers as the Demon to the Clockmaker."
 },
 {
  "id1": "summoner",
  "id2": "courtier",
  "reason": "If the living Summoner has no ability, the Storyteller has the Summoner ability."
 },
 {
  "id1": "summoner",
  "id2": "engineer",
  "reason": "If the living Summoner is removed from play, the Storyteller has the Summoner ability."
 },
 {
  "id1": "summoner",
  "id2": "hatter",
  "reason": "If the Summoner creates a second living Demon, deaths tonight are arbitrary."
 },
 {
  "id1": "summoner",
  "id2": "kazali",
  "reason": "If the Summoner creates a second living Demon, deaths tonight are arbitrary."
 },
 {
  "id1": "summoner",
  "id2": "lordoftyphon",
  "reason": "If a Lord of Typhon is summoned, they must neighbor a Minion & their other neighbor becomes an evil Minion."
 },
 {
  "id1": "summoner",
  "id2": "pithag",
  "reason": "If the Summoner creates a second living Demon, deaths tonight are arbitrary."
 },
 {
  "id1": "summoner",
  "id2": "poppygrower",
  "reason": "If the Poppy Grower is alive on the 3rd night, the Summoner chooses which Demon but not which player."
 },
 {
  "id1": "summoner",
  "id2": "preacher",
  "reason": "If the living Summoner has no ability, the Storyteller has the Summoner ability."
 },
 {
  "id1": "summoner",
  "id2": "pukka",
  "reason": "The Summoner may summon a Pukka on the 2nd night instead of the 3rd."
 },
 {
  "id1": "summoner",
  "id2": "zombuul",
  "reason": "If the Summoner summons a dead player into the Zombuul, the Zombuul has already \"died once\"."
 },
 {
  "id1": "vizier",
  "id2": "alsaahir",
  "reason": "The Storyteller doesn't declare the Vizier is in play."
 },
 {
  "id1": "vizier",
  "id2": "courtier",
  "reason": "If the Vizier loses their ability, they learn this, and cannot die during the day."
 },
 {
  "id1": "vizier",
  "id2": "fearmonger",
  "reason": "The Vizier wakes with the Fearmonger, learns who they choose and cannot choose to immediately execute that player."
 },
 {
  "id1": "vizier",
  "id2": "investigator",
  "reason": "The Storyteller doesn't declare the Vizier is in play."
 },
 {
  "id1": "vizier",
  "id2": "politician",
  "reason": "The Politician might register as evil to the Vizier."
 },
 {
  "id1": "vizier",
  "id2": "preacher",
  "reason": "If the Vizier loses their ability, they learn this, and cannot die during the day."
 },
 {
  "id1": "vizier",
  "id2": "zealot",
  "reason": "The Zealot might register as evil to the Vizier."
 },
 {
  "id1": "vortox",
  "id2": "banshee",
  "reason": "If the Vortox kills the Banshee, all players learn that the Banshee has died."
 },
 {
  "id1": "widow",
  "id2": "damsel",
  "reason": "If the Widow is (or has been) in play, the Damsel is poisoned."
 },
 {
  "id1": "widow",
  "id2": "poppygrower",
  "reason": "If the Poppy Grower has their ability, the Widow does not see the Grimoire."
 },
 {
  "id1": "yaggababble",
  "id2": "exorcist",
  "reason": "If the Exorcist chooses the Yaggababble, the Yaggababble does not kill tonight."
 }
]
```

#### §4.1 The 80 jinxes missing from the bundle

| # | pair | official text |
|---|---|---|
| 1 | `alchemist`+`boffin` | If the Alchemist has the Boffin ability, the Alchemist does not learn what ability the Demon has. |
| 2 | `alchemist`+`marionette` | An Alchemist-Marionette has no Marionette ability & the Marionette is in play. |
| 3 | `alchemist`+`mastermind` | An Alchemist-Mastermind has no Mastermind ability & the Mastermind is not-in-play. |
| 4 | `alchemist`+`organgrinder` | If the Alchemist has the Organ Grinder ability, the Organ Grinder is in play. If both are sober, both are drunk. |
| 5 | `alchemist`+`spy` | An Alchemist-Spy has no Spy ability & a Spy is in play. After each execution, a living Alchemist-Spy may publicly guess a living player as the Spy. If correct, the Demon must choose the Spy tonight. |
| 6 | `alchemist`+`widow` | An Alchemist-Widow has no Widow ability & a Widow is in play. After each execution, a living Alchemist-Widow may publicly guess a living player as the Widow. If correct, the Demon must choose the Widow tonight. |
| 7 | `alchemist`+`wraith` | An Alchemist-Wraith has no Wraith ability & a Wraith is in play. After each execution, a living Alchemist-Wraith may publicly guess a living player as the Wraith. If correct, the Demon must choose the Wraith tonight. |
| 8 | `alhadikhia`+`mastermind` | If the Al-Hadikhia dies by execution, and the Mastermind is alive, the Al-Hadikhia chooses 3 good players tonight: if all 3 choose to live, evil wins. Otherwise, good wins. |
| 9 | `alhadikhia`+`princess` | If the Princess nominated & executed a player on their 1st day, no one dies to the Al-Hadikhia tonight. |
| 10 | `vizier`+`alsaahir` | The Storyteller doesn't declare the Vizier is in play. |
| 11 | `riot`+`atheist` | During a riot, if the Storyteller is nominated, players vote. If they are "about to die", the game ends. If not, they nominate again. |
| 12 | `leviathan`+`banshee` | Each night*, the Leviathan chooses an alive good player (different to previous nights): a chosen Banshee dies & gains their ability. |
| 13 | `riot`+`banshee` | Each night*, Riot chooses an alive good player (different to previous nights): a chosen Banshee dies & gains their ability. |
| 14 | `boffin`+`goon` | If the Demon has the Goon ability, they can’t turn good due to this ability. |
| 15 | `boffin`+`ogre` | The Demon cannot have the Ogre ability. |
| 16 | `boffin`+`politician` | The Demon cannot have the Politician ability. |
| 17 | `boffin`+`villageidiot` | If there is a spare token, the Boffin can give the Demon the Village Idiot ability. |
| 18 | `plaguedoctor`+`boomdandy` | If the Storyteller would gain the Boomdandy ability, a player becomes the Boomdandy. |
| 19 | `cannibal`+`princess` | If the Cannibal nominated, executed, & killed the Princess today, the Demon doesn’t kill tonight. |
| 20 | `summoner`+`clockmaker` | The Summoner registers as the Demon to the Clockmaker. |
| 21 | `vizier`+`courtier` | If the Vizier loses their ability, they learn this, and cannot die during the day. |
| 22 | `mathematician`+`drunk` | The Mathematician learns if the Drunk’s ability yielded false info or failed to work properly. |
| 23 | `legion`+`engineer` | If Legion is created, all evil players become Legion. If Legion is in play, the Engineer starts knowing this but has no ability. |
| 24 | `summoner`+`engineer` | If the living Summoner is removed from play, the Storyteller has the Summoner ability. |
| 25 | `plaguedoctor`+`eviltwin` | If the Storyteller would gain the Evil Twin ability, a player becomes the Evil Twin. |
| 26 | `leviathan`+`exorcist` | If the Leviathan nominates and executes the Exorcist-chosen player, good wins. |
| 27 | `riot`+`exorcist` | If Riot nominates and executes the Exorcist-chosen player, good wins. |
| 28 | `plaguedoctor`+`fearmonger` | If the Storyteller would gain the Fearmonger ability, a Minion gains it, and learns this. |
| 29 | `vizier`+`fearmonger` | The Vizier wakes with the Fearmonger, learns who they choose and cannot choose to immediately execute that player. |
| 30 | `plaguedoctor`+`goblin` | If the Storyteller would gain the Goblin ability, a Minion gains it, and learns this. |
| 31 | `pithag`+`goon` | If the Pit-Hag turns an evil player into the Goon, they can't turn good due to their own ability. |
| 32 | `leviathan`+`grandmother` | If the Leviathan is in play and the Grandchild dies by execution, evil wins. |
| 33 | `legion`+`hatter` | If Legion is created, all evil players become Legion. If Legion is in play, the Hatter has no ability. |
| 34 | `leviathan`+`hatter` | The Leviathan cannot enter play after day 5. |
| 35 | `lilmonsta`+`hatter` | If the Hatter dies & the Demon chooses Lil' Monsta, they also choose a Minion to become. |
| 36 | `summoner`+`hatter` | If the Summoner creates a second living Demon, deaths tonight are arbitrary. |
| 37 | `leviathan`+`innkeeper` | If the Leviathan nominates and executes an Innkeeper-protected player, good wins. |
| 38 | `riot`+`innkeeper` | If Riot nominates and executes an Innkeeper-protected player, good wins. |
| 39 | `vizier`+`investigator` | The Storyteller doesn't declare the Vizier is in play. |
| 40 | `summoner`+`kazali` | If the Summoner creates a second living Demon, deaths tonight are arbitrary. |
| 41 | `leviathan`+`king` | If the Leviathan is in play, and at least 1 player is dead, the King learns an alive character each night. |
| 42 | `riot`+`king` | If Riot is in play, and at least 1 player is dead, the King learns an alive character each night. |
| 43 | `magician`+`legion` | If the Magician is in play, during the Demon info step, Legion wake in separate groups. Each group learns which players are good, but does not learn the Magician. |
| 44 | `legion`+`minstrel` | If Legion died by execution today, Legion keeps their ability, but the Minstrel might learn they are Legion. |
| 45 | `legion`+`politician` | The Politician might register as evil to Legion. |
| 46 | `legion`+`preacher` | If the Preacher chooses Legion, Legion keeps their ability, but the Preacher might learn they are Legion. |
| 47 | `legion`+`summoner` | If Legion is summoned, all evil players become Legion. |
| 48 | `legion`+`zealot` | The Zealot might register as evil to Legion. |
| 49 | `leviathan`+`monk` | If the Leviathan nominates and executes the Monk-protected player, good wins. |
| 50 | `leviathan`+`pithag` | The Leviathan cannot enter play after day 5. |
| 51 | `leviathan`+`soldier` | If the Leviathan nominates and executes the Soldier, good wins. |
| 52 | `lilmonsta`+`psychopath` | If the Psychopath is babysitting Lil' Monsta, they die when executed. |
| 53 | `lilmonsta`+`vizier` | If the Vizier is babysitting Lil' Monsta, they die when executed. |
| 54 | `lleech`+`mastermind` | If the Mastermind is alive and the Lleech host dies by execution, the Lleech lives but loses their ability. |
| 55 | `summoner`+`lordoftyphon` | If a Lord of Typhon is summoned, they must neighbor a Minion & their other neighbor becomes an evil Minion. |
| 56 | `magician`+`marionette` | If the Magician is alive, the Demon doesn't know which neighbor is the Marionette. |
| 57 | `magician`+`vizier` | If the Vizier is in play, the Magician has no ability but is immune to the Vizier's ability. |
| 58 | `magician`+`wraith` | After each execution, the living Magician may publicly guess a living player as the Wraith. If correct, the Demon must choose the Wraith tonight. |
| 59 | `mathematician`+`marionette` | The Mathematician learns if the Marionette’s ability yielded false info or failed to work properly. |
| 60 | `plaguedoctor`+`marionette` | If the Storyteller would gain the Marionette ability, one of the Demon's good neighbors becomes the Marionette. |
| 61 | `mastermind`+`vigormortis` | A Mastermind that has their ability keeps it if the Vigormortis dies. |
| 62 | `riot`+`mayor` | The Mayor may choose to stop the riot. If they do so when only 1 Riot is alive, good wins. Otherwise, evil wins. |
| 63 | `riot`+`monk` | If Riot nominates and executes the Monk-protected player, good wins. |
| 64 | `pithag`+`ogre` | If the Pit-Hag turns an evil player into the Ogre, they can't turn good due to their own ability. |
| 65 | `recluse`+`ogre` | If the Recluse registers as evil to the Ogre, the Ogre learns that they are evil. |
| 66 | `spy`+`ogre` | The Spy registers as evil to the Ogre. |
| 67 | `pithag`+`politician` | If the Pit-Hag turns an evil player into the Politician, they can't turn good due to their own ability. |
| 68 | `summoner`+`pithag` | If the Summoner creates a second living Demon, deaths tonight are arbitrary. |
| 69 | `pithag`+`villageidiot` | If there is a spare token, the Pit-Hag can create an extra Village Idiot. If so, the drunk Village Idiot might change. |
| 70 | `plaguedoctor`+`spy` | If the Storyteller would gain the Spy ability, a Minion gains it, and learns this. |
| 71 | `plaguedoctor`+`wraith` | If the Storyteller would gain the Wraith ability, a Minion gains it, and learns this. |
| 72 | `vizier`+`politician` | The Politician might register as evil to the Vizier. |
| 73 | `summoner`+`poppygrower` | If the Poppy Grower is alive on the 3rd night, the Summoner chooses which Demon but not which player. |
| 74 | `summoner`+`preacher` | If the living Summoner has no ability, the Storyteller has the Summoner ability. |
| 75 | `vizier`+`preacher` | If the Vizier loses their ability, they learn this, and cannot die during the day. |
| 76 | `summoner`+`pukka` | The Summoner may summon a Pukka on the 2nd night instead of the 3rd. |
| 77 | `recluse`+`sage` | The Recluse might register as the Demon to the Sage. |
| 78 | `riot`+`soldier` | If Riot nominates and executes the Soldier, good wins. |
| 79 | `summoner`+`zombuul` | If the Summoner summons a dead player into the Zombuul, the Zombuul has already "died once". |
| 80 | `vizier`+`zealot` | The Zealot might register as evil to the Vizier. |

#### §4.2 The 39 bundled jinxes whose text is stale

| pair | bundled text (stale) | official text (current) |
|---|---|---|
| `alchemist`+`summoner` | The Alchemist can not have the Summoner ability. | The Alchemist-Summoner does not get bluffs, and chooses which Demon but not which player. If they die before this happens, evil wins. [No Demon] |
| `scarletwoman`+`alhadikhia` | If there are two living Al-Hadikhias, the Scarlet Woman Al-Hadikhia becomes the Scarlet Woman again. | If there would be two Demons, one of which was the Scarlet Woman, the Scarlet Woman becomes the Scarlet Woman again. |
| `marionette`+`balloonist` | If the Marionette thinks that they are the Balloonist, +1 Outsider might have been added. | If the Marionette thinks that they are the Balloonist, an Outsider might have been added during setup. |
| `vortox`+`banshee` | If the Vortox kills the Banshee, all players still learn that the Banshee has died. | If the Vortox kills the Banshee, all players learn that the Banshee has died. |
| `heretic`+`baron` | The Baron might only add 1 Outsider, not 2. | Only 1 jinxed character can be in play. |
| `plaguedoctor`+`baron` | If the Storyteller gains the Baron ability, up to 2 Townsfolk players become not-in-play Outsiders. | If the Storyteller would gain the Baron ability, up to two players become Outsiders. |
| `boffin`+`cultleader` | If the Demon has the Cult Leader ability, they can't turn good due to their own ability. | If the Demon has the Cult Leader ability, they can’t turn good due to this ability. |
| `boffin`+`drunk` | If the Boffin gives the Demon the Drunk ability, the Demon thinks they have been given a different not-in-play Townsfolk ability. | The Demon cannot have the Drunk ability. |
| `boffin`+`heretic` | The Boffin can not give the Demon the Heretic ability. | The Demon cannot have the Heretic ability. |
| `bountyhunter`+`kazali` | An evil Townsfolk is only created if the Kazali chooses the Bounty Hunter. | If the Kazali turns the Bounty Hunter into a Minion, an evil Townsfolk is not created. |
| `cerenovus`+`goblin` | The Cerenovus may choose to make a player mad that they are the Goblin, instead of a good character. | The Cerenovus may choose to make a player mad that they are the Goblin. |
| `mathematician`+`chambermaid` | The Chambermaid learns if the Mathematician wakes tonight or not, even though the Chambermaid wakes first. | The Chambermaid can detect if the Mathematician will wake tonight. |
| `summoner`+`courtier` | If the Summoner is drunk on the 3rd night, the Summoner chooses which Demon, but the Storyteller chooses which player becomes that Demon. | If the living Summoner has no ability, the Storyteller has the Summoner ability. |
| `yaggababble`+`exorcist` | If the Exorcist chooses the Yaggababble, the Yaggababble does not kill due to their public phrase that night. | If the Exorcist chooses the Yaggababble, the Yaggababble does not kill tonight. |
| `scarletwoman`+`fanggu` | If the Fang Gu chooses an Outsider and dies, the Scarlet Woman does not become the Fang Gu. | If there would be two Demons, one of which was the Scarlet Woman, the Scarlet Woman remains the Scarlet Woman. |
| `leviathan`+`farmer` | If Leviathan is in play and a Farmer dies by execution, a good player becomes a Farmer that night. | Each night*, the Leviathan chooses an alive good player (different to previous nights): a chosen Farmer uses their ability but does not die. |
| `riot`+`farmer` | If Riot kills the Farmer, a good player becomes a Farmer tonight. | Each night*, Riot chooses an alive good player (different to previous nights): a chosen Farmer uses their ability but does not die. |
| `riot`+`grandmother` | If Riot kills the Grandchild, the Grandmother dies too. | If Riot is in play and the Grandchild dies by execution, evil wins. |
| `heretic`+`lleech` | If the Lleech has poisoned the Heretic, and the Lleech dies, the Heretic remains poisoned. | Only 1 jinxed character can be in play. |
| `heretic`+`pithag` | A Pit-Hag can not create a Heretic. | Only 1 jinxed character can be in play. |
| `marionette`+`huntsman` | If the Marionette thinks that they are the Huntsman, the Damsel was added. | If the Marionette thinks that they are the Huntsman, the Damsel was added during setup. |
| `marionette`+`kazali` | If the Kazali chooses to create a Marionette, they must choose one of their alive neighbors to be it. | If there would be a Marionette in play, they enter play after the Demon & must start as their neighbor. |
| `leviathan`+`mayor` | If Leviathan is in play and no execution occurs on day 5, good wins. | If the Leviathan and the Mayor are alive on day 5 & no execution occurs, good wins. |
| `leviathan`+`ravenkeeper` | If Leviathan is in play and the Ravenkeeper dies by execution, they wake that night to use their ability. | Each night*, the Leviathan chooses an alive player (different to previous nights): a chosen Ravenkeeper uses their ability but does not die. |
| `leviathan`+`sage` | If Leviathan is in play and the Sage dies by execution, they wake that night to use their ability. | Each night*, the Leviathan chooses an alive good player (different to previous nights): a chosen Sage uses their ability but does not die. |
| `lilmonsta`+`magician` | Each night, the Magician chooses a Minion: if that Minion and Lil' Monsta are alive, that Minion babysits Lil' Monsta. | If the Magician is alive, the Storyteller chooses which Minion babysits Lil' Monsta. |
| `marionette`+`lilmonsta` | The Marionette neighbors a Minion, not the Demon. The Marionette is not woken to choose who takes the Lil' Monsta token. | If there would be a Marionette in play, they enter play after the Demon & must start as their neighbor. |
| `lilmonsta`+`poppygrower` | If the Poppy Grower is in play, Minions don't wake together. They are woken one by one, until one of them chooses to take the Lil' Monsta token. | If Lil' Monsta & the Poppy Grower are alive, Minions wake one by one, until one of them chooses to take the Lil' Monsta token. |
| `lilmonsta`+`scarletwoman` | If there are 5 or more players alive and the player holding the Lil' Monsta token dies, the Scarlet Woman is given the Lil' Monsta token tonight. | If Lil' Monsta dies with 5 or more players alive, the Scarlet Woman babysits Lil' Monsta for the rest of the game. |
| `lleech`+`slayer` | If the Slayer slays the Lleech's host, the host dies. | If the Slayer slays the Lleech host, the host dies. |
| `mathematician`+`lunatic` | The Mathematician learns if the Lunatic attacks a different player(s) than the real Demon attacked. | The Mathematician learns if the Lunatic attacks a different player than the real Demon attacked. |
| `magician`+`spy` | When the Spy sees the Grimoire, the Demon's and Magician's character tokens are removed. | When the Spy sees the Grimoire, the Demon and Magician's character tokens are removed. |
| `magician`+`widow` | When the Widow sees the Grimoire, the Demon's and Magician's character tokens are removed. | When the Widow sees the Grimoire, the Demon and Magician's character tokens are removed. |
| `marionette`+`summoner` | The Marionette neighbors the Summoner, not the Demon. The Summoner knows who the Marionette is. | If there would be a Marionette in play, they enter play after the Demon & must start as their neighbor. |
| `plaguedoctor`+`scarletwoman` | If the Demon dies while the Storyteller has the Scarlet Woman ability, a living Minion becomes the Demon. | If the Storyteller would gain the Scarlet Woman ability, a Minion gains it, and learns this. |
| `spy`+`poppygrower` | If the Poppy Grower is in play, the Spy does not see the Grimoire until the Poppy Grower dies. | If the Poppy Grower has their ability, the Spy does not see the Grimoire. |
| `widow`+`poppygrower` | If the Poppy Grower is in play, the Widow does not see the Grimoire until the Poppy Grower dies. | If the Poppy Grower has their ability, the Widow does not see the Grimoire. |
| `riot`+`ravenkeeper` | If Riot kills the Ravenkeeper, the Ravenkeeper wakes that night to use their ability. | Each night*, Riot chooses an alive good player (different to previous nights): a chosen Ravenkeeper uses their ability but does not die. |
| `riot`+`sage` | If Riot kills the Sage, the Sage wakes that night to use their ability. | Each night*, Riot chooses an alive good player (different to previous nights): a chosen Sage uses their ability but does not die. |

### §5 — `night_guide.json`

#### §5.1 Entries that contradict the wiki

Verified page-by-page (`action=parse&prop=wikitext`, `== Summary ==` + `== How to Run ==`).

| id | severity | app says | wiki says | fix |
|---|---|---|---|---|
| `undertaker` | **P0** | "Only act if a player was executed today (marked Died today), **even if they did not die from it**." | "The player must have died from execution for the Undertaker to learn who they are." / "the execution does not cause a death (in which case the Undertaker learns nothing)" | "Only act if a player **died by** execution today. If the executed player survived (Fool, Devil's Advocate, Sailor, Mayor bounce…), the Undertaker does not wake." |
| `vortox` | **P0** | "If the Vortox is drunk or poisoned, no one dies, **but information is still false**." | "While the Vortox is alive, you must give false information…" + the core drunk/poisoned rule (no ability). The wiki's "even if they are drunk or poisoned, it must be false" clause is about the *Townsfolk*, not the Vortox. | "If the **Vortox** is drunk or poisoned it has no ability tonight: nobody dies, Townsfolk info is **not** forced false, and the no-execution loss does not trigger at this dusk." |
| `shabaloth` | **P0** | "they learn no new information and their once-per-game abilities remain as they were" | "The regurgitated player regains their ability, even a 'once per game' ability already used. If they had a 'first night only' or 'start knowing' ability, they may use it again." + "They wake later tonight if they normally would. If they wake on the first night only, they wake now to use their ability." | "The regurgitated player **regains their ability**, including a spent once-per-game. If their ability is first-night-only or start-knowing, **re-run it now**. Otherwise they wake later tonight at their normal position." |
| `butler` | **P0** | "This still applies even if the Butler is drunk or poisoned." | Butler page + the core drunk/poisoned rule: no ability means no restriction. Wiki, same principle: "The Butler is dead. Because dead players have no ability, the Butler may vote with their vote token at any time." | Drop the sentence. Replace with the wiki's actual ST instruction: "If the Butler accidentally votes illegally, **tally the vote anyway** — a missing vote outs them." |
| `shabaloth` | **P1** | "they point to two players, and both die" | "In the order chosen, each chosen player dies" | Order matters (the wiki's Tea Lady example: the neighbour dies first, *then* the Tea Lady, so both die). Record the order. |
| `shabaloth` | **P1** | "(Innkeeper, sober Sailor, Devil's Advocate does not apply at night)" | — | The hard-coded protection list omits Tea Lady / Monk / Soldier / Fool / Lleech-host. Delegate to `StatusEffects.deathNotes` instead of listing them in prose. |
| `mezepheles` | **P1** | "the first good player to say it **publicly**" | "If a good player says this word, **either publicly or privately**, they turn evil that night." | Say "publicly or privately (including privately to you)". |
| `mezepheles` | **P1** | — | "If the Mezepheles is drunk or poisoned at night when a player would turn evil, the player stays good — the Mezepheles has 'used their ability' and may not turn a player evil later on." | Add. This is a once-per-game burn the app must record even though nothing visibly happened. |
| `mezepheles` | **P2** | "Consider waking them alongside or before the Demon so the evil team can learn its new member" | Not in the How to Run. | Remove, or mark as optional ST flavour. The wiki *does* add two steps the app omits: "Turn their character token upside down" and "remove their night token from the night sheet". |
| `harpy` | **P1** | (other night) "If yesterday's madness was broken, one or both of yesterday's targets **might die tonight**." | "**Tomorrow**, if the player marked MAD is not mad that the player marked 2ND is evil, you may kill one or both players." | The Harpy penalty is a **day-time** ST decision, not a night kill. Surface it at day start as an unresolved prompt. |
| `harpy` | **P2** | "They point to two players in order" | "The Harpy points to one player, **then** another player" + "the Harpy player chooses one player at a time, not two at once" | Reword; add "the Harpy may choose a dead player — then only the living one can be killed." |
| `philosopher` | **P2** | "swap in that character's token, keep the 'Is the Philosopher' reminder by it" (both branches) | "If they pointed to an icon of a character **not in play**, swap the Philosopher token with the chosen character token and mark them with the IS THE PHILOSOPHER reminder. If they pointed to an icon of a character **in play**, the player of the chosen character becomes drunk… (You can now use the duplicated character's reminders for the Philosopher)" | Split the two branches; the token swap is only for the not-in-play case. Fix the label to the official global `Is The Philosopher`. |
| `philosopher` | **P2** | "point to a good character on their sheet" | "point at any Townsfolk icon or any Outsider icon" | Say Townsfolk **or Outsider** explicitly. Add: "If the Philosopher regains their ability via the Bone Collector, or acts twice via the Barista, they may choose again — the same ability or a new one." |
| `exorcist` | **P2** | omits showing **THIS CHARACTER SELECTED YOU** to the chosen Demon | official otherNightReminder | Add. |
| `lunatic` | **P2** | omits **THESE ARE YOUR MINIONS** and **THESE CHARACTERS ARE NOT IN PLAY** | official firstNightReminder: "Show the \*THESE ARE YOUR MINIONS\* token. Point to any players. Show the \*THESE CHARACTERS ARE NOT IN PLAY\* token. Show 3 good character tokens." | Add both; the Lunatic gets its **own** 3 bluffs, distinct from the Demon's. |
| `duchess` | **P2** | omits **THIS CHARACTER SELECTED YOU** | official otherNightReminder | Add. |
| `stormcatcher` | **P0** | see D4 | | Rewrite both the reminder and the guide entry, including the "not in play" branch. |

More contradictions, same method (each quote checked against that character's wiki page):

| id | severity | app says | wiki says | fix |
|---|---|---|---|---|
| `preacher` | **P0** | "All Minions ever chosen by a sober, healthy Preacher have no ability." | "All Minions marked NO ABILITY have no ability **while the Preacher is alive**." / "If the Preacher becomes drunk or poisoned, preached Minions regain their abilities until the Preacher is sober and healthy." | "Minions marked No Ability lose it only **while the Preacher is alive, sober and healthy**; they get their abilities back if the Preacher dies or is drunk/poisoned, and lose them again if the Preacher recovers." |
| `fanggu` | **P0** | "That player dies, unless they are an Outsider and no Outsider has jumped yet this game: in that case the Fang Gu dies instead…" | "If the Fang Gu attacks an Outsider but that Outsider **does not die**, that Outsider does not become an evil Fang Gu and the Fang Gu does not die." | "…unless they are an Outsider who **actually dies** from this attack. If protection stops the death there is no jump and the Fang Gu lives." (`NightScreen.kt:485-497` offers the jump unconditionally.) |
| `king` | **P0** | "Skip this step if a Poppy Grower is in play and alive." | The Poppy Grower's only stated exception is "Do not do the Minion Info and Demon Info steps." `king` is a separate night-order step *after* `demoninfo`, and there is no King–Poppy Grower jinx in the official list. | Delete the sentence — the Demon learns the King on night 1 even with a living Poppy Grower. |
| `xaan` | **P0** | "When the night number equals X, replace it with the X reminder: every Townsfolk is poisoned…" | "The Xaan needs to be **alive** in order to poison." | Add the alive check. Also: "If the number of Outsiders changes during the game, the Xaan poisons on the night corresponding to the number of Outsiders **during setup**." |
| `summoner` | **P0** | "If the Summoner is dead or drunk/poisoned on night 3, no Demon is created (or you choose, per your ruling) — by default evil loses without a Demon." | "if the Summoner becomes unable to create a Demon (due to dying, becoming drunk on night 3 etc.) **good wins**." Example: "On the first day, the Summoner is executed. Good wins." | Not an ST judgement call, and the death case resolves **immediately**, not on night 3: "If the Summoner dies before creating the Demon, good wins at once." Also add "The new Demon does not learn which players are Minions, or vice versa." |
| `duchess` | **P0** | "Only act if players visited the Duchess today." | "If **exactly three** visitors cannot be decided upon, then the Duchess does not act tonight." / "If more or less than three players volunteer to visit, do not add these reminders." | "Only act if **exactly three** players volunteered; otherwise place no reminders and skip the Duchess." (Needs `Visitor` ×2 — D8.) |
| `courtier` | **P1** | "place the Drunk 3 reminder token by the chosen player and mark the Courtier with No Ability" | "At dusk on the next night, remove the DRUNK 3 reminder, **and** the Courtier loses their ability — mark them with the NO ABILITY reminder" (the mark goes on *after* the three nights, because the drunkenness is suspended while the Courtier is drunk/poisoned and resumes if they recover) | Place `No Ability` only when the three nights end. Also: official counts **up** — "mark them with DRUNK 1… the next night, replace DRUNK 1 with DRUNK 2" — the app counts down. |
| `cerenovus` | **P1** | "if they do not make a convincing effort, you may execute them" | "If you execute them during the day before the normal execution happens, go to the night phase. (There is a maximum of one execution per day.)" | Add: "Declare it publicly; it **counts as that day's one execution**, so if it happens first the day ends immediately." (Same for `mutant`.) |
| `lunatic` | **P1** | "wake the real Demon, point to each marked player so the Demon knows the Lunatic's choices" | "Wake the real Demon, **point at the Lunatic, show the Lunatic token to the real Demon**, and point at the players that the Lunatic chose." | Add showing the Lunatic token and pointing at the Lunatic — the user's "should show the real demon who they chose" is only half of what the rules require. |
| `barista` | **P1** | "or wake them twice or double their ability's effect" | "If they have already used a 'once per game' ability, they may use that ability **again**. If they have a 'once per game' ability but have not used it yet, they may use it **twice** before dusk." | Spell out the once-per-game cases. Also: "This player must get true information, **even if a Vortox is in play**." |
| `alchemist` | **P1** | "Keep the 'Is The Alchemist' reminder by them" | "mark the Alchemist with the IS THE ALCHEMIST reminder **and swap the Alchemist token with this Minion token and turn it upside down**." | Add the token swap. Also: when the ST asks the Alchemist to choose differently, "**The Alchemist must do so**", and the Minion ability "can duplicate an **in-play** Minion ability", not only a not-in-play one. |
| `bountyhunter` | **P1** | (other) "point to another evil player, moving the Know reminder to them" | "The Bounty Hunter **cannot learn the same evil player twice**." | Add the constraint (the picker must exclude previously-shown seats). First night also omits "turn one Townsfolk character token upside down, to represent that they are evil." |
| `farmer` | **P1** | "Swap their character token for a Farmer; they are now the Farmer with the full ability." | "When a player becomes a Farmer, they are no longer their old character… **Any ongoing effects of their old ability immediately end.**" | Add: remove the old character's reminders and end its ongoing effects. |
| `leviathan` | **P1** | "Track executions of good players… if a second good player is ever executed, evil wins immediately." | "**All types of execution count, even if the player doesn't die.** A player executed due to the Virgin, or due to revealing that they are the Mutant, is still executed. An executed player who lives due to the Pacifist is still executed." | Add — the app will under-count otherwise. |
| `plaguedoctor` | **P1** | — | "If applicable, **add a night token to the night sheet**." | The Storyteller-gained Minion ability must be given its own recurring night step. |
| `king` | **P1** | — | "When the number of dead players equals or exceeds the number of alive players, **add a night token** to the King's entry on the night sheet." / "If a King is created mid-game, the Demon learns who the King is that night." | Add both. |
| `librarian`, `washerwoman` | **P2** | never removes the info tokens | "Remove the Librarian's/Washerwoman's reminder tokens when convenient." | Add. |
| `dreamer` | **P2** | "If the Dreamer is drunk or poisoned, **or the Vortox is in play**, neither token needs to be correct." | Vortox example: "The Dreamer's information **must be false**." Plus the rule the app omits: "If the Dreamer chooses a Townsfolk or Outsider, the false character token is any Minion or Demon. If they choose a Minion or Demon, the false character token is a Townsfolk or Outsider." | Under a Vortox false is mandatory, not optional; and the false token's team is constrained. |
| `nightwatchman` | **P2** | "If the Nightwatchman is drunk or poisoned, **do not wake the target**; the target learns nothing, but the use is still spent." | Vortox example: the target still wakes and is pointed at the **wrong** player ("Sarah learns that Lewis is the Nightwatchman… the information is false"). | Wake the target and point at a different player. Also omits "remove their night token from the night sheet." |
| `devilsadvocate` | **P2** | never announces the save | "If a player marked SURVIVES EXECUTION is executed, **declare that the player was executed but remains alive.** (Do not say why.)" | Add a dawn/execution-time announcement. |
| `ojo` | **P2** | "point to tokens on a script sheet or let them name it" | "The Ojo player **points to a character icon on their character sheet**." Plus "If there are multiple copies of a particular character in play… only one of those characters dies", and if the named character is not in play "the Storyteller will almost always kill a living good player". | Constrain the picker to the character sheet and add the two rulings. |
| `villageidiot` | **P2** | — | "If all sober Village Idiots exit play, the remaining drunk Village Idiot remains drunk. If a sober Village Idiot becomes drunk or poisoned by other means, the drunk Village Idiot remains drunk." / "If a Village Idiot is created mid-game, only one is created." | The drunk marker never moves; add. |
| `chef`, `oracle` | **P2** | "If … drunk or poisoned, show a false number." | Drunk/poisoned info is the ST's *choice* (the app already words this correctly for Librarian/Washerwoman: "you may show false information"); false is mandatory only under a Vortox. | "…you **may** show a false number." For `oracle`, also: the count includes tonight's deaths, evil Travellers, and turned-evil Townsfolk/Outsiders ("count Townsfolk and Outsider tokens that are upside-down"). |
| `fanggu` | **P2** | "place the 'Once' reminder to mark that the jump has happened" | "Put the ONCE reminder **in the center of the Grimoire**… this reminder stays there for the rest of the game. **Don't remove it, even if the Fang Gu dies or changes character.**" Also "the new Fang Gu does not learn which players are Minions." | `NightScreen.kt:496` places `Once` on the *target seat* via `placeExclusiveReminder`, so it moves and can be lost. It should be a game-level flag. |

Verified clean in this pass: `spy`, `grandmother`, `snakecharmer`.

Third verification pass (same method):

| id | severity | app says | wiki says | fix |
|---|---|---|---|---|
| `barber` | **P0** | "The Demon either shakes their head no or points to two players (**neither may be a Demon**)." | "The Demon **may choose themself** to swap." / "The Demon may not choose **another** Demon player to swap." | "…points to two players, either of whom may be **themself**, but not another Demon player." |
| `boffin` | **P0** | "run the granted ability at its usual place in the night order **for the rest of the game**" | "**While the Boffin is alive**, the Demon has a single Townsfolk ability or Outsider ability." | "…for as long as the Boffin is alive and sober." Also: "If the **Boffin** is drunk or poisoned, the Demon temporarily loses this good ability" (the app says only that a drunk Demon keeps it). |
| `hatter` | **P0** | "each may choose a new character of their own type… **with no duplicates of in-play characters**" | "Each player either shakes their head no or points to another character of the same type… **If a second player would end up with the same character as another player, shake your head no and gesture for them to choose again.**" / "If it is already in play, the player with that character must choose a new character." | "…each may keep their character or choose any other of the same type; if two evil players would collide, make the **later** chooser pick again — including a player who wanted to stay as they are." |
| `lycanthrope` | **P0** | "Remember one good player registers as evil (the Faux Paw reminder) to info abilities while the Lycanthrope lives" | "While the Lycanthrope lives, one good player registers as evil. **They cannot be killed by the Lycanthrope.**" | "Faux Paw registers as evil to **everything**, including the Lycanthrope's own choice — picking them kills nobody and the Demon still kills tonight." Also missing the setup step: "During setup, mark one good player with the FAUX PAW reminder." |
| `toymaker` | **P0** | "Track with the Final Night: No Attack reminder that the Demon **has used** their obligatory no-attack night." | "Add the Toymaker token to the Grimoire, and **mark the Demon** with FINAL NIGHT: NO ATTACK." / "**If they choose not to attack, remove** the FINAL NIGHT: NO ATTACK reminder." | The token means the obligation is **outstanding**, not spent: place it at the start of the game, remove it the first night the Demon declines to attack. Also: `night_guide` has **no `first` entry at all** for the Toymaker, so its mandatory first-night job — "resolve the Minion info and Demon info steps even though there are fewer than seven players" — is never shown (this is the same gap as the missing firstNight list entry, §3). |
| `apprentice` | **P1** | "run them as that character from now on" | "The Apprentice does not literally become the character whose ability they gain… other characters' abilities that detect characters would detect the Apprentice **as the Apprentice**." | Add: "They gain only the ability; they remain the Apprentice for every detecting ability, and can be exiled but not executed." Also "Only abilities listed on the character sheet may be gained." |
| `kazali` | **P1** | "they point to each player they want as a Minion and pick which Minion character each becomes" | "Repeat until **the normal number of Minions** exist." / "Only Minions **that are on the script** may be chosen. **Duplicate Minion characters are not allowed.**" | Add all three constraints to the picker. |
| `lleech` | **P1** | "If anything would kill the Lleech while its host lives, the Lleech does not die" | Example: "The Lleech is made drunk by the Philosopher… The drunk Lleech is executed and **dies**, and good wins." | Add: "…unless the Lleech is drunk or poisoned, in which case the host is not poisoned and the Lleech dies normally." Also missing: "If the Lleech is executed, tell the group the player lives, but not why." |
| `pithag` | **P1** | "If a new Demon is created, deaths tonight are arbitrary — you choose who dies" | "you may choose any players to kill **or to protect** throughout the night to balance the game. **Additional deaths are considered attacks from the Pit-Hag.**" | Matters for Sage / Ravenkeeper / Godfather triggers — the extra deaths are Pit-Hag attacks, not Demon kills. |
| `po` | **P1** | "they must point to three players tonight, and all three die" | "**In the order chosen**, each chosen player dies… **Remove the 3 ATTACKS reminder.**" | Resolve in order and clear the token (which also needs `Dead` ×3, D8). |
| `professor` | **P1** | "place the Alive reminder token, mark the Professor with No Ability, and at dawn announce that the player is now alive" | "**Remove their shroud.**" / "(**Do not say why.**)" / "The resurrected player **regains their ability, even a 'once per game' ability they used already**." / "(They **wake later tonight** if they normally would. If they wake on the first night only, **they wake now** to use their ability.)" / "remove their **night token from the night sheet**." | This is the user's exact complaint. Full corrected procedure in §5.1 of the Shabaloth row plus: re-insert the resurrected player into tonight's remaining order, or re-run their first-night step immediately. |
| `vigormortis` | **P1** | "choose one of their Townsfolk neighbours to be poisoned" | "The **closest clockwise or closest counterclockwise Townsfolk** to the Minion becomes poisoned… One Townsfolk per Minion will **always** be poisoned this way, as neighboring Outsiders, Minions, or Travellers are **skipped**." + "If the Vigormortis dies or otherwise loses their ability, those players become healthy again." | The neighbour search skips non-Townsfolk (alive or dead) — the app implies literal adjacency, which can wrongly yield "no valid neighbour". |
| `yaggababble` | **P1** | "Resolve the deaths at the Yaggababble's place in the night order" | "If the Yaggababble says this phrase, the Storyteller **may kill a player any time afterwards, until dawn**." | Allow a day-time kill. Also no drunk/poisoned rule is given at all: "If the Yaggababble is drunk or poisoned, players cannot die, even if the Yaggababble was sober when they said their phrase" (and the converse). |
| `bureaucrat` | **P2** | "Tomorrow, that player's vote counts as 3 votes." | "Count this **out loud**, as normal." / "The player with the triple vote **loses it immediately if the Bureaucrat dies**, including if the Bureaucrat is exiled." | Add both. |
| `cultleader` | **P2** | "if every good player has publicly joined the cult, the Cult Leader's team wins" | "Once per day, the Cult Leader may publicly choose to form a cult… **run a vote in the same way that you would for an Exile**. If all good players raise their hand, declare which team has won." | It is a formal vote, not an informal count. Also: keep the Grimoire token turned to their current alignment. |
| `harlot` | **P2** | "you may choose that both the Harlot and the chosen player die tonight" | "If the Demon reveals to the Harlot, you **should not end the game** by killing them." | Add. |
| `juggler` | **P2** | "If the Juggler is drunk or poisoned, or the Vortox is in play, give a false number." | "If the Juggler made their guesses while drunk or poisoned, but is **sober and healthy when their ability triggers** that night, then the Storyteller still gives them **true** information." | Only tonight's state matters. |
| `pixie` | **P2** | "they gain that character's ability when that player dies (then place the Has Ability reminder)" | "**replace** the MAD reminder with the HAS ABILITY reminder… and will **wake at night when this Townsfolk would normally wake**" / "**the Pixie does not learn this**, and is not told that they have gained a new ability." | Replace (not add) the token, say nothing, and start waking them at that character's step. |
| `poisoner` | **P2** | (other) "Remove the Poisoned reminder from the previously poisoned player." | "**Each dusk**, the poisoned player becomes healthy — remove their POISONED reminder." | Removal is at **dusk**, before any night step runs, so earlier steps do not treat a stale target as poisoned. (`EXPIRES_AT_DUSK` already does this — the prose contradicts the engine.) |
| `tinker` | **P2** | "At any time, including tonight, you may decide the Tinker dies: place the Dead reminder token and announce the death at dawn." | "If this is **during the day**, immediately declare that the Tinker has died. If this is during the night, mark the Tinker with DEAD and wait until dawn." + "The Tinker cannot die from their ability **while protected** from death" + "never kill the Tinker when it would end the game." | Split day vs night, add the protection check. |
| `barber` | **P2** | — | "**If the Barber dies, mark them with the HAIRCUTS TONIGHT reminder.**" / "If a player's alignment does not match the colour of their character token, turn it upside-down." / "If there is more than one living Demon, the Storyteller chooses which Demon makes the swap." | Add. |
| `hatter` | **P2** | "show the 'You are' info token and their new character token" | "Show them the **THIS CHARACTER SELECTED YOU** info token, then the **Hatter** token." + "**Remove the TEA PARTY TONIGHT reminder.**" | Wrong info token, and the tracking token is never cleared. |

Verified clean in this pass: `fortuneteller`, `ravenkeeper`, `gambler`, `sage`, `shugenja`,
`highpriestess`, `balloonist`, `widow`.

Fourth verification pass (this pass independently re-derived the `undertaker` and `vortox`
P0s above from the wiki, including the Rules → States page: *"A drunk or poisoned player has
no ability."*):

| id | severity | app says | wiki says | fix |
|---|---|---|---|---|
| `legion` | **P0** | "executions **fail (nobody dies)** if only evil players voted" | "If the vote tally is enough to make a player about to die but only evil players voted, **declare that the vote tally is zero**." Example: "3 Legion and no good players vote to execute Julian. Julian is not executed. **Alex, who has 2 votes, 1 of which is a good player, is executed instead.**" | Zeroing the tally is not the same as a failed execution: a *later* nominee with fewer votes can still go to the block. |
| `lordoftyphon` | **P0** | "Wake each of the Lord of Typhon's **two** neighbors one at a time" | "wake the **appropriate number** of players directly clockwise and anti-clockwise from the Lord of Typhon." | At 10-12 players there are 3 Minions and at 13-15 there are 4, so it is not two. Wake as many outward on each side as the Minion count requires. Also missing: "During setup, remove all Minion tokens and add Townsfolk or Outsider tokens", then "**replace these players' good character tokens with these Minion tokens**", and "Then, do the Minion Info and Demon Info steps as normal." |
| `organgrinder` | **P0** | "you announce only **whether the nomination has enough votes**" | "do not reveal how many players voted, **nor if the nominee is 'about to die'**… mark them with the ABOUT TO DIE reminder… When nominations are closed, declare that the player marked ABOUT TO DIE is executed." | Say nothing at all until nominations close. The `About To Die` token already exists in `characters.json` and is never mentioned by the guide. |
| `organgrinder` | **P0** | "During the day, run **all** votes with eyes closed" | "If the Organ Grinder is **drunk**, the vote happens with eyes open, as normal. The Storyteller makes no comment as to whether the Organ Grinder is dead or alive." | Eyes-closed voting only while sober — and the Organ Grinder chooses its own drunkenness each night, so this flips night to night. |
| `poppygrower` | **P0** | "even if the Poppy Grower dies while drunk or poisoned it is **your judgement** whether evil learns each other" | "If the Poppy Grower is drunk or poisoned when they die, Demons and Minions **do not** learn who each other are, since the Poppy Grower has no ability that night." | Not a judgement call — skip the step. |
| `assassin` | **P1** | "even kills the Zombuul properly on its first death registering" | Zombuul: "When the Zombuul would die for any reason, they actually don't die, but the Storyteller acts as if they died." No almanac text supports a carve-out. | Drop the claim, or label it explicitly as a house ruling. |
| `banshee` | **P1** | — | "If all good players are dead, **the game continues**. Good can still win due to the Banshee being able to nominate." | Add — this changes the win check. |
| `chambermaid` | **P1** | — | "**Do not wake the Chambermaid** if there are not two players alive to be chosen (due to the Mastermind, Zombuul, etc.)." | Add a wake condition. |
| `gnome` | **P1** | "the Gnome may choose to kill that nominator immediately" | "it is the Gnome's responsibility to speak up. **The Storyteller may not prompt them.**" / "**before you have started the voting process**" / "the nominator dies immediately. **Voting for execution still occurs.**" | All three. (And this belongs in the `day` channel, D17.) |
| `legion` | **P1** | no `first` entry at all | "During the first night, during the Demon Info step, **let all Legion players make eye contact**. (You may want to point to the non-Legion players so that Legion knows who they are.)" | Add a first-night / Demon-Info note. |
| `magician` | **P1** | — | "If the Poppy Grower dies and the Demon and Minions learn who each other are mid-game, **the Magician ability has an effect that night, just as if it was the first night**." | Neither `magician.first` nor `poppygrower.other` mentions it — the mid-game evil-info step must also point to the Magician. |
| `mathematician` | **P1** | prose never mentions the tokens | "Each time a character's ability works abnormally due to another character's ability, **mark them with an ABNORMAL reminder**… Show fingers equaling the number of characters with ABNORMAL reminders… **Remove all ABNORMAL reminders.**" | The five `Abnormal` tokens (D8) exist precisely for this and are invisible in the guide. |
| `ogre` | **P1** | — | "If the Ogre pointed to an evil player, **flip the Ogre's character token upside down**." The FRIEND reminder is an *optional* rule; under the base rule the alignment is fixed (Example: "The Ogre remains good"). | Add the flip and make the `Friend` token's optional status explicit. |
| `pukka` | **P1** | "protection prevents the death but **not the poisoning**" | "The Innkeeper prevents the Pukka from killing a poisoned player, **then that player is no longer poisoned**." | The poison ends either way. |
| `pukka` | **P1** | "…and becomes healthy **upon death**" | "Players that the Pukka kills are **still poisoned at their time of death**… you may need to keep the POISONED reminder by the DEAD reminder **until their death ability is resolved**." | Ravenkeeper/Sage triggers must resolve *as poisoned*, then the token comes off. |
| `sailor` | **P1** | — | "If the sober Sailor is executed, **declare that this player is executed but remains alive.** (Do not say why.)" | Same missing announcement pattern as `devilsadvocate` and `lleech`. |
| `scarletwoman` | **P1** | never places the token | "mark the Scarlet Woman with the **IS THE DEMON** reminder and refer to that Demon's 'How to Run' instructions." | Add — and note `characters.json` labels it `Demon`, not `Is The Demon` (§6). |
| `witch` | **P1** | "the Witch does not wake" (at 3 alive) | "As soon as just three players are left alive, **the Witch's curse is immediately removed**, and the Witch acts no more." Example: "Later that night, after the Demon kills a player, only three players are alive, so **the curse is removed**." | The `Cursed` token must come off mid-night, not at the next dusk. |
| `assassin`, `engineer`, `huntsman`, `seamstress` | **P2** | "skip them" / marks No Ability | "**remove their night token from the night sheet**" | The night sheet should drop the step once the once-per-game is spent. |
| `clockmaker` | **P2** | "counting seats in the shorter direction" | Travellers count as steps, but "the Clockmaker learns a '2', because **evil Travellers are not Minions**." | Add. |
| `engineer` | **P2** | — | must name the **correct number** of Minions for the player count; only characters **on the script**; the number of evil players is unchanged; choosing an already-in-play character does nothing **and still burns the use**. | Add all four. |
| `fearmonger` | **P2** | "nominates and executes the marked player" | "If the chosen player is **executed but does not die**, the chosen player's team still loses." | Add. |
| `gossip` | **P2** | "If the statement was false, or the Gossip is drunk or poisoned, no one dies" | "If the Gossip made a true statement during the day **while drunk or poisoned, but is sober and healthy when their ability triggers that night**, the Storyteller still kills a player." Plus the daytime step "put the Gossip's DEAD reminder in the center of the left side of the Grimoire", and "we **recommend** that you choose a character that will actually die" (the app states it as a rule). | Exactly the user's "make it easy to write down all the gossips" request — this needs a `day` channel entry that records the statement and its truth value, then feeds the night step. |
| `huntsman` | **P2** | — | "If the **Damsel** is drunk or poisoned but the Huntsman is sober and healthy, the Damsel can still become a Townsfolk." | Add. |
| `investigator` | **P2** | "Show them the character token of a Minion that is **in play**" | Example: a Recluse can register as a Minion that is **not** in play ("The Investigator learns that either Brianna or Marianna is the Poisoner"). | Widen to any Minion on the script. |
| `legion` | **P2** | — | ST kill guidance ("aim to get to three players alive… On the final day, if the players don't execute, kill a good player"), the `About To Die` token trick, and "If only one good player remains alive, the Storyteller may declare that evil wins." | The `About To Die` token is in `characters.json` and never referenced. |
| `magician` | **P2** | — | "The Storyteller can point to the Magician and the evil players **in any order**, so that the evil players won't know which player is the Magician." | Add. |
| `organgrinder` | **P2** | — | "Dead players may vote once if they have a vote token. **Their vote token is removed at the end of the day instead of after the vote.**" / "Players are not allowed to use other methods to determine who is voting, such as touch or sound." | Add. |
| `poppygrower` | **P2** | — | "An evil Traveller still learns which player is the Demon when that Traveller enters play." Minions "**make eye contact**" — that is how they learn each other. | Add. |
| `steward` | **P2** | "point to the good player marked with the Know reminder" | "**While preparing the first night, put the KNOW reminder by any good character token.**" | The token is never placed. |
| `sweetheart` | **P2** | "choose 1 player to be drunk from now on" | official otherNightReminder: "If the Sweetheart died, a player became drunk **immediately**. If you haven't done this yet, do so now." | If the Sweetheart died by execution the drunkenness covers the rest of that day too. |
| `thief` | **P2** | — | "**Count this out loud**, as normal" / "The player with the negative vote changes back **immediately if the Thief dies**" / "Exiles are never affected by abilities, so the player with the negative vote can support exiles." | Add all three. |
| `undertaker` | **P2** | — | "In other editions, there may be more than one execution per day (in which case **the Storyteller chooses** which character to show)" / "If the Drunk is executed, the Undertaker is shown the **Drunk** character token." | Add. |
| `vortox` | **P2** | "every piece of Townsfolk info you give **this game** must be wrong" | "**While the Vortox is alive**…" / "The Vortox does **not** affect information gained by other means, such as when the Storyteller explains the rules, or when a player's character or alignment changes." | Scope it to "while alive and sober", and exclude non-ability information. |
| `witch` | **P2** | — | "abilities do not affect exiles" — a cursed player who calls for an **exile** does not die. | Add. |
| `wizard` | **P2** | "Once the wish has been made, the Wizard never acts again" | "**If the wish is declined, prompt the Wizard to wish again.**" Plus: signal acceptance ("say 'Your wish is granted'…") and the clue "is declared **publicly**". | A declined wish does not spend the ability. |

Verified clean in this pass: `acrobat`.

Fifth and final verification pass (this one also cross-checked the wiki's Rules → States
page, *"A drunk or poisoned player has no ability"*, which independently confirms the
`butler`, `vortox` and `bonecollector` P0s):

| id | severity | app says | wiki says | fix |
|---|---|---|---|---|
| `moonchild` | **P0** | "If the Moonchild was drunk or poisoned **when they made the choice**, no one dies." | "If the Moonchild is **sober and healthy at night** but was drunk or poisoned when they chose a player today, **that player dies.** If the Moonchild is drunk or poisoned at night but was sober and healthy when they chose today, that player doesn't die." | Only tonight's state matters. (Same shape as the `juggler` and `gossip` errors — the app consistently keys these off the *day* state.) |
| `princess` | **P0** | "If the Princess was drunk or poisoned **during that day**, the Demon kills as normal." | "If the Princess is drunk during the day, then sober at night, **they prevent the Demon from killing.** If the Princess is sober during the day, but drunk at night, they do not." | Same inversion. |
| `bonecollector` | **P0** | "(even if they are drunk or poisoned they **have** the ability; run it normally)" | Rules → States: "A drunk or poisoned player has no ability." | A drunk/poisoned Bone Collector grants nothing; a drunk/poisoned *target* wakes but malfunctions. |
| `damsel` | **P0** | "each Minion may make **one public guess** of who the Damsel is" | "No matter how many Minions are in play, **they only get one guess, total.**" | One guess for the whole evil team — mark `Guess Used` after the first wrong public guess. |
| `alhadikhia` | **P0** | "you announce their choice aloud ('The first chooses to live') before putting them to sleep" | "They either nod or shake their head. **Put them to sleep.**" / "**Each dawn**, declare which players marked 1, 2 and 3 are alive and which are dead." | Answers are silent during the night; only the resulting statuses are declared at dawn. |
| `alhadikhia` | **P0** | "Kill each player who chose to die; if all three chose to live, all three die instead." | "If they chose to live, **remove their shroud (if any)** — players may be **brought back to life** this way — and if they choose to die, add a shroud… **If all three players are alive** (none have a shroud) then add a shroud to all three." / "If a player chose to die but did not die, they count as alive for this calculation." | Resolve each answer immediately (a "live" answer resurrects a dead chosen player), then re-check. |
| `lilmonsta` | **P0** | "they silently agree on **one of them** to babysit Lil' Monsta" | "The majority will (eventually) point to **one player**." / "If a **good** player babysits Lil' Monsta, they 'are the Demon' but they remain good." | Any player, not just a Minion. |
| `riot` | **P0** | "each nominee dies immediately and may (on day 3, they must) nominate another player straight away; if day 3 ends with good not having won, evil wins" | "**On day 3**, Minions become Riot & nominees die but nominate an alive player immediately." / "repeat this process until **all Riot are dead**, or **just 2 players are alive**." | This is the retired Riot (matching the stale ability text, D1). Nominees die only on day 3, and the day runs nomination-to-nomination until good wins (all Riot dead) or evil wins (2 alive). |
| `stormcatcher` | **P0** | "If the named character is not in play, evil players still wake and learn **a player of your choice** (usually bluff-supporting)." | "show the evil player the **THESE CHARACTERS ARE NOT IN PLAY** info token and the good character token, then put the evil player to sleep." | No player is pointed to at all. See D4. |
| `exorcist` | **P1** | "the Demon does not act tonight and **does not wake** for their ability" | "**Any other Demon abilities still function** — such as the Zombuul staying alive if killed, the **Pukka killing a player they attacked on a previous night**, or the Shabaloth regurgitating a player." + "will still wake if they need to due to other characters' abilities." | Directly relevant to the user's Pukka report: an Exorcised Pukka still kills last night's poisoned target. `NightOrder.kt:149-153` appends "the Demon does not act tonight" unconditionally. |
| `innkeeper` | **P1** | "Remove the Protected and Drunk reminder tokens **from the previous night**" (i.e. at the start of the next night) | "**At dawn**, remove the SAFE reminders. **At dusk**, remove the DRUNK reminder." / "The Innkeeper only protects players **at night, not the day**." | The engine already gets this right (`GameActions.kt:220,234`); the prose contradicts it and implies day-time protection. |
| `zombuul` | **P1** | "Only wake the Zombuul if no-one died today… mark this in the grimoire" | "Each day, if a player dies, mark them with DIED TODAY. (**If the Zombuul 'dies' by execution, they register as dead, so mark the Zombuul with DIED TODAY.**)" / "Declare that they died, but **do not add a shroud**. (Flip the life token on the Town Square, as normal.)" | The Zombuul's own fake death must be marked and announced, with the life token flipped but no shroud. |
| `flowergirl` | **P1** | "place the 'Demon voted' reminder as soon as the Demon raises their hand to vote, and clear it each dawn" | "**Each dawn, mark the Flowergirl with DEMON NOT VOTED**, and remove DEMON VOTED, if any. Each day, if the Demon **votes for any execution**, replace DEMON NOT VOTED with DEMON VOTED." | The default token is `Demon Not Voted`, placed every dawn — the app never places it. And a raised hand lowered before the tally, or a hand raised on an **exile**, does not count. |
| `towncrier` | **P1** | "Place the 'Minion nominated' reminder during the day when a Minion nominates" | "**Each dawn, mark the Town Crier with MINIONS NOT NOMINATED**, and remove MINION NOMINATED, if any." / "Remove MINION NOMINATED **after waking them**." | Same default-token pattern. |
| `eviltwin` | **P1** | "evil wins if the good twin is executed" | "**A dead Evil Twin has no ability**, so evil doesn't win if the Good Twin is later executed." | Add the alive condition. |
| `bonecollector` | **P1** | ends at placing `No ability` on the Bone Collector | "**The next dusk**, the chosen player loses their ability — remove the HAS ABILITY reminder." / "**If the Bone Collector dies**, that player no longer has the ability they regained." | Add both (and see §6.1 — `bonecollector`/`Has Ability` belongs in `EXPIRES_AT_DUSK`). |
| `damsel` | **P1** | (first) "The Damsel themself is not woken." | official firstNightReminder: "**If the Damsel was chosen by the Huntsman**, show the *YOU ARE* info token & their new character token." | Add the Huntsman exception. And per the Huntsman page, "If the **Damsel** is drunk or poisoned but the Huntsman is sober and healthy, the Damsel **can still** become a Townsfolk" — the app's "if the Damsel was drunk or poisoned… the change may not occur" is wrong. |
| `marionette` | **P1** | "Do not wake the Marionette — they think they are the good character they drew" | "The Marionette is not woken due to character abilities that would confirm that they are a Minion **eg. Snitch, Preacher, Lil' Monsta, Poppy Grower, Hatter, Damsel**." | Enumerate the steps that must skip the Marionette; the engine skips only `MINION_INFO` (`NightOrder.kt:60-95`). |
| `vizier` | **P1** | "if a good player voted on **any** nomination, the Vizier may reveal themselves to have that nominee executed" | "**After a vote is tallied**, if the Vizier chooses to execute the nominee (**and at least one good player voted** on that nomination), they are executed immediately. **This counts as the 1 execution allowed each day.**" / "**No more nominations, votes, or executions occur today.**" | Scope to the nomination just tallied, and end the day. |
| `alhadikhia` | **P1** | — | "**All players must be silent** when the Al-Hadikhia acts at night… This period lasts from when the Storyteller first declares that a player has been chosen, until the Storyteller says that it ends." + "Declare that the time of silence has ended." | Add both declarations, and "declare, for each of the 3 marked players, whether they are alive or dead — **even if nothing changed**". |
| `lilmonsta` | **P1** | (first) — | "On the first night, **skip the MINION INFO and DEMON INFO steps**." | The engine runs both today. |
| `lilmonsta` | **P1** | — | "The player marked IS THE DEMON registers as the Demon. **If they die, declare that the game is over and good has won.**" (and a dead player chosen to babysit ends the game) | Add the win condition. |
| `riot` | **P1** | "you may wake all Minion players one at a time and show the 'You are' info and the Riot token" | "…then **replace all Minion tokens with Riot tokens**." / "Tell them to nominate again and **publicly count down '3… 2… 1…'**" / "If the players do not nominate at all on the 3rd day, or if a nominated player does not nominate before their time runs out, **nominate a player** yourself." | Add all three. |
| `stormcatcher` | **P1** | no announcement at all | "**At the start of the game, declare that the Storm Catcher is in play.** Add the Storm Catcher token to the Grimoire. **Declare which good character is favoured.**" | Add. Note a genuine source conflict here: the **wiki How-to-Run still says the `SAFE` reminder and the `THIS PLAYER IS` info token**, while the newer `roles.json` says `Stormcaught` and `THESE CHARACTERS ARE NOT IN PLAY`. `roles.json` is the more recently updated source and matches the reclassification to `loric`; flag this one for a human decision rather than silently picking. |
| `empath`, `flowergirl`, `towncrier` | **P2** | "show a false number instead" / "give the opposite (false) answer" when drunk or poisoned | Rules → States: "You're **not required** to give incorrect info, but you can — and you usually should!" | Make it "you **may**". (The Vortox half of the sentence, where false is mandatory, is correct.) |
| `monk` | **P2** | — | "The Monk's protection also prevents **all other harmful effects of the Demon's ability**, such as poisoning or turning the protected player evil." | Add — it matters for No Dashii, Vigormortis, Fang Gu, Lord of Typhon. |
| `imp` | **P2** | — | "If the Imp attacks a **dead** player at night, **let them do so**." / "This new Imp **does not act that same night**." | Add. |
| `innkeeper` | **P2** | — | "An Innkeeper that chooses **themself** might become drunk, which means they have no ability and may die tonight — and the other player they chose to protect isn't safe either." | Add. |
| `zombuul` | **P2** | — | "If a **drunk or poisoned** Zombuul dies, good wins." / a 'dead' Zombuul means the game continues even with just two other players alive. | Add — both affect the win check. |
| `philosopher` | **P2** | (other night) — | "If the Philosopher **dies**, the player made drunk by the Philosopher becomes sober — remove the DRUNK reminder." | The first-night text has this; the other-night text drops it. |
| `nodashii` | **P2** | "update if seating deaths change nothing but characters change" (garbled) | "regardless of whether they are **alive or dead**" / "If a No Dashii dies or otherwise loses their ability, those two players become **healthy**." / setup: "While preparing the first night… mark them with POISONED reminders." | Rewrite; the mandatory night-1 marking currently appears only inside the *other*-night text, so a storyteller never sees it on night 1. |
| `amnesiac` | **P2** | "cold, warm, hot" | "…or '**bingo**' if the guess is spot on." + "If the Amnesiac guesses their ability but the wording is different, **still tell them they guessed correctly**." | Add the fourth answer. |
| `choirboy` | **P2** | "point to a wrong player **or do not wake them**" | Example: "The **drunk** Choirboy wakes and wrongly learns that the General is the Demon." | Not waking leaks the drunkenness. Also add the ordering: put the Demon to sleep, *then* wake the Choirboy. |
| `princess` | **P2** | "place the Doesn't Kill reminder" (on the Princess) | "mark **the Demon** with the DOESN'T KILL reminder" | Wrong seat. |
| `vizier` | **P2** | "At dawn (or at the start of the game)" | "**When the first night has ended.**" | And "the Vizier may reveal themselves" is redundant — the Vizier is announced publicly on night 1. |
| `lilmonsta` | **P2** | "If the Minions cannot agree, the token stays where it is or **you decide**" | "If they can not reach a unanimous decision, **the Storyteller decides**." | Drop the first alternative. |
| `eviltwin` | **P2** | "the good twin does not learn which of them is evil beyond this" | The good twin **is** shown the Evil Twin token — the app's own `shows[]` card says "This player is EVIL". | Self-contradictory; fix the prose. |

Verified clean in this pass: `general`, `godfather`, `knight`, `noble`, `snitch` (the
Snitch's "before the Demon receives its bluffs" is correct — the official order really is
`minioninfo, snitch, demoninfo`).

**Totals across all five passes: all 116 night-guide entries were checked against their wiki
page (Summary + How to Run + Examples). 136 defects — 30 P0 contradictions that would make a
storyteller run the game wrong, 51 P1 omissions of a mandatory How-to-Run step, and 55 P2
clarity/label defects — across 99 distinct characters. 17 entries were verified clean:
`spy`, `grandmother`, `snakecharmer`, `fortuneteller`, `ravenkeeper`, `gambler`, `sage`,
`shugenja`, `highpriestess`, `balloonist`, `widow`, `acrobat`, `general`, `godfather`,
`knight`, `noble`, `snitch`.**

Three failure patterns account for most of the P0s and are worth fixing as classes rather
than one row at a time:

1. **Drunk/poisoned is keyed off the wrong moment.** `moonchild`, `princess`, `juggler`,
   `gossip` and `mezepheles` all resolve on a day-time choice; the rule is always that only
   the state **at the moment the ability triggers** matters. Conversely `vortox`, `butler`
   and `bonecollector` assert that an ability still works while drunk or poisoned, which the
   Rules → States page flatly denies: *"A drunk or poisoned player has no ability."*
2. **A source character's death or drunkenness does not undo its effects.** `preacher`,
   `boffin`, `eviltwin`, `bureaucrat`, `thief`, `vigormortis`, `nodashii`, `philosopher` and
   `bonecollector` all have "while X is alive and sober" scoping that the guide states as
   permanent. This wants a single engine concept — a token whose validity is conditional on
   its source seat — not nine prose fixes.
3. **The default/"nothing happened" token is never placed.** `flowergirl`
   (`Demon Not Voted`), `towncrier` (`Minions Not Nominated`), `steward` (`Know`),
   `lycanthrope` (`Faux Paw`), `nodashii` (`Poisoned` ×2 at setup), `toymaker`
   (`Final Night: No Attack`), `marionette`/`drunk` (identity tokens) and `mathematician`
   (`Abnormal`) all require the storyteller to place a token *before* anything happens. The
   app only ever describes placing tokens in reaction to a choice.

#### §5.2 Proposed `day` / `setup` / `reference` guide channels

`NightGuideEntry` (`NightGuide.kt:36-40`) currently has `first` and `other`. Add three
optional siblings with the identical `GuideNight` shape, and rename the file
`character_guide.json` (keep `night_guide.json` as an alias for one release):

```kotlin
@Serializable
data class CharacterGuideEntry(
    val first: GuideNight? = null,      // first night
    val other: GuideNight? = null,      // other nights
    val setup: GuideNight? = null,      // before night 1: bag changes, token swaps, ST picks
    val day: GuideNight? = null,        // day-phase procedure the ST runs or watches for
    val reference: GuideNight? = null,  // passive/always-on rules, no ST action
)
```

- **`setup`** — shown on `SetupScreen` for every in-play character that has one, and again as
  a checklist before the "Dusk" button on night 1. Needed by: `drunk` (pick the seat, place
  `Is The Drunk`, tell them their fake Townsfolk), `marionette` (must neighbour the Demon;
  place `Is The Marionette`), `lunatic` (choose which Demon they think they are, and their own
  bluffs), `hermit` (`-0 or -1 Outsider` — which?), `atheist` (no evil in the bag), plus
  `baron` / `godfather` / `sentinel` / `xaan` / `kazali` / `vigormortis` / `fanggu` /
  `balloonist` / `huntsman` / `choirboy` / `legion` / `villageidiot` / `bootlegger` /
  `gardener` / `pope` / `tor` (setup modifiers and companions), `eviltwin`, `stormcatcher`
  (name the character), `djinn` (write the special rule down), `mezepheles` (write the secret
  word), `snitch`, `magician`, `boffin` (which ability the Demon gets).
- **`day`** — shown on `DayScreen` as a "watch for" strip, and consumed by the night step that
  needs it. Needed by all 55 characters with no entry today, in particular: `slayer` (public
  claim → resolve), `artist` / `fisherman` / `savant` / `puzzlemaster` (private ST visit,
  once-per-game), `alsaahir` (public guess), `gossip` (public statement — the user's explicit
  request: record it **even when Gossip is not in play**), `juggler` (day-1 guesses, consumed
  by the night-2 step), `mutant` (madness break → may execute), `cerenovus` / `harpy`
  (madness break → ST kill, **during the day**), `klutz`, `goblin` (public claim → `Claimed`
  token), `psychopath`, `boomdandy`, `mastermind`, `butcher` / `gunslinger` / `judge` /
  `matron` / `gangster` / `bishop` / `voudon` / `beggar` / `deviant` / `scapegoat` / `gnome`
  (traveller day powers), `golem`, `virgin`, `zealot`, `politician`, `cannibal`, `minstrel`,
  `pacifist`, `saint`, `heretic`, `fool`, `soldier`, `tealady`, `mayor`, `recluse`, `goon`,
  `damsel`.
- **`reference`** — always-on rules with no ST action, shown in `LibraryScreen` /
  `ReferenceScreen` and in the seat sheet: `recluse`, `spy`, `politician`, `zealot`,
  `heretic`, `atheist`, `soldier`, `tealady`, `mayor`, plus the 12 non-acting Fabled/Loric
  (`bootlegger`, `deusexfiasco`, `djinn`, `doomsayer`, `ferryman`, `fibbin`, `fiddler`,
  `gardener`, `hellslibrarian`, `revolutionary`, `sentinel`, `spiritofivory`).

Coverage rule to enforce in a test: **every id in `characters.json` must have at least one of
`first`/`other`/`setup`/`day`/`reference`**, and `first` is present **iff** the id is in
`firstNight`, `other` **iff** in `otherNight`.

#### §5.3 The 55 characters with no guide entry today, and the channel each needs

Every one of these has something the storyteller must run, watch for, or announce. None of
them has a single line of guidance in the app today.

| id | team | proposed channel(s) | what the ST must be told |
|---|---|---|---|
| `alsaahir` | townsfolk | day | public guess of Minion/Demon seats — resolve immediately |
| `angel` | fabled | setup+reference | ST announces the protected new players; Something Bad |
| `artist` | townsfolk | day | once-per-game private yes/no question; mark spent |
| `atheist` | townsfolk | setup+reference | no evil in the bag; ST may break rules |
| `baron` | minion | setup | +2 Outsiders |
| `beggar` | traveler | day+reference | must use a vote token; dead may gift theirs |
| `bishop` | traveler | day | only the ST nominates; 1 opposing player per day |
| `boomdandy` | minion | day | on execution all but 3 die, 10-to-1 countdown, pointing |
| `bootlegger` | fabled | setup+reference | homebrew rules; setup=true; ? tokens |
| `buddhist` | fabled | setup+reference | announce affected players; 2-minute silence each day |
| `butcher` | traveler | day | may nominate again after the 1st execution |
| `cannibal` | townsfolk | day | gains the executee ability; poisoned if they were evil |
| `deusexfiasco` | fabled | reference | setup=true; Whoopsie token |
| `deviant` | traveler | day | cannot die by exile if funny |
| `djinn` | fabled | setup+reference | write the special rule down and tell everyone |
| `doomsayer` | fabled | day | each living player may once publicly choose a player who dies |
| `drunk` | outsider | setup | pick the seat, place Is The Drunk, give the fake Townsfolk |
| `ferryman` | fabled | day | final day: all dead regain their vote token |
| `fibbin` | fabled | reference | once per game 1 good player gets incorrect info; No Ability token |
| `fiddler` | fabled | day | Demon picks an opponent; all players choose a side |
| `fisherman` | townsfolk | day | once-per-game private advice; mark spent |
| `fool` | townsfolk | reference | first death does not kill; No Ability token after |
| `gangster` | traveler | day | once per day kill a neighbour with the other neighbour agreeing |
| `gardener` | fabled | setup | setup=true; ST assigns all characters |
| `goblin` | minion | day | public Goblin claim when nominated -> Claimed token |
| `golem` | outsider | day | one nomination per game; non-Demon nominee dies |
| `goon` | outsider | reference | 1st player to choose them each night is drunk until dusk |
| `gunslinger` | traveler | day | after the 1st tally, kill a player who voted |
| `hellslibrarian` | fabled | reference | Something Bad for talking during silence |
| `heretic` | outsider | reference | win/lose inverted |
| `hermit` | outsider | setup | -0 or -1 Outsider; then wakes at every Outsider step (D10b) |
| `judge` | traveler | day | once per game force the current nomination to pass or fail |
| `klutz` | outsider | day | on learning they died, publicly choose a player |
| `mastermind` | minion | day | Demon executed -> one extra day |
| `matron` | traveler | day | up to 3 seat swaps; restricts private chat |
| `mayor` | townsfolk | day+reference | 3 alive + no execution -> good wins; night bounce |
| `minstrel` | townsfolk | day | Minion executed -> everyone else drunk until dusk tomorrow |
| `mutant` | outsider | day | madness break -> may execute (counts as the day execution) |
| `pacifist` | townsfolk | day | executed good players might not die |
| `politician` | outsider | reference | may change alignment at the end |
| `psychopath` | minion | day | before nominations may publicly kill; duels if executed |
| `puzzlemaster` | outsider | day | one guess for the drunk player -> learn the Demon (may be wrong) |
| `recluse` | outsider | reference | misregistration |
| `revolutionary` | fabled | setup+reference | mark the 2 Aligned players; Register Falsely? token |
| `saint` | outsider | reference | executed -> good loses |
| `savant` | townsfolk | day | two daily facts, one true one false |
| `scapegoat` | traveler | day | may be executed instead of a same-alignment player |
| `sentinel` | fabled | setup | setup=true; +1/-1 Outsider |
| `slayer` | townsfolk | day | public slay -> resolve; mark No Ability |
| `soldier` | townsfolk | reference | safe from the Demon |
| `spiritofivory` | fabled | setup+reference | no more than 1 extra evil; No More Evil token |
| `tealady` | townsfolk | reference | both good neighbours cannot die (Cannot Die x2) |
| `virgin` | townsfolk | day | 1st nomination by a Townsfolk -> instant execution; No Ability after |
| `voudon` | traveler | day+reference | only the Voudon and the dead vote; no 50% majority |
| `zealot` | outsider | reference | must vote on every nomination at 5+ alive |

The 116 ids that *do* have entries all have exactly the right night channels — the
`first`/`other` keys match the app's night lists perfectly, in both directions. The coverage
problem is entirely the missing day/setup/reference dimension.

### §6 — Reminder-label consistency

**Rule to adopt: `characters.json` is the single source of truth for label strings, in the
official Title Case, and code must never contain a label literal that is not in
`characters.json`.** Enforce with test 1 below.

Code sites that must change together with the data (all are `(sourceId, label)` pairs):

| file:line | current | after |
|---|---|---|
| `GameActions.kt:219` | `"monk" to "Safe"` | unchanged (official `Safe`) |
| `GameActions.kt:220` | `"innkeeper" to "Protected"` | `"innkeeper" to "Safe"` |
| `GameActions.kt:221` | `"exorcist" to "Chosen"` | unchanged |
| `GameActions.kt:222-224` | `"lunatic" to "Attack 1"/"Attack 2"/"Attack 3"` | `"lunatic" to "Chosen"` (official label, ×3 copies) |
| `GameActions.kt:236` | `"devilsadvocate" to "Survives execution"` | `"Survives Execution"` |
| `GameActions.kt:232-241` | `poisoner/Poisoned`, `sailor/Drunk`, `innkeeper/Drunk`, `butler/Master`, `witch/Cursed`, `cerenovus/Mad`, `harpy/Mad`, `harpy/2nd`, `goblin/Claimed` | unchanged — all match official |
| `GameActions.kt:70` | `PlacedReminder("snakecharmer","Poisoned")` | unchanged |
| `GameShell.kt:366` | `"fortuneteller", "Red herring"` | `"Red Herring"` |
| `GameShell.kt:394,400` | `"drunk", "Is the Drunk"` | `"Is The Drunk"` |
| `GameShell.kt:461,466` | `"marionette", "Is the Marionette"` | `"Is The Marionette"` — **already broken today**, the data says `Is The Marionette` |
| `NightScreen.kt:264,272` | `"No ability"` (generic once-per-game "Mark spent") | `"No Ability"` |
| `NightScreen.kt:504,514` | `"professor", "No ability"` | `"No Ability"` |
| `NightScreen.kt:496` | `"fanggu", "Once"` | unchanged |
| `StatusEffects.kt:66-69` | matches lower-cased `"safe"`, `"protected"`, `"survives execution"`, `"can not die"` | `"safe"` now covers Monk **and** Innkeeper — distinguish by `sourceId`, not label; `"can not die"` → `"cannot die"` |
| `StatusEffects.kt:75,154` | `equals("No ability", true)` | case-insensitive already; switch the literal to `No Ability` |
| `StatusEffects.kt:124,144,158,162` | `"Grandchild"`, `"Cursed"`, `"Fear"`, `"Mad"` | unchanged — all match official |

Data label corrections (all 45 divergences; the pure Title Case ones are grouped):

- **Title case only** (app → official): `Red herring`→`Red Herring`;
  `No ability`→`No Ability` (slayer, virgin, courtier, fool, professor, assassin, judge,
  artist, seamstress, bonecollector); `Died today`→`Died Today` (undertaker, godfather,
  zombuul); `3 votes`→`3 Votes`; `Negative vote`→`Negative Vote`;
  `Everyone is drunk`→`Everyone Is Drunk`; `Survives execution`→`Survives Execution`;
  `Nominate good`/`Nominate evil`→`Nominate Good`/`Nominate Evil`;
  `Demon voted`/`Demon not voted`→`Demon Voted`/`Demon Not Voted`;
  `Minions not nominated`/`Minion nominated`→`Minions Not Nominated`/`Minion Nominated`;
  `Haircuts tonight`→`Haircuts Tonight`; `Has ability`→`Has Ability` (vigormortis,
  bonecollector); `3 attacks`→`3 Attacks`; `Is the Apprentice`→`Is The Apprentice`;
  `Is the Drunk`→`Is The Drunk`; `Is the Philosopher`→`Is The Philosopher`.
- **Renamed tokens** (not just case — these change what the ST sees):
  `innkeeper` `Protected`→`Safe`; `tealady` `Can not die`→`Cannot Die`;
  `scarletwoman` `Demon`→`Is The Demon`; `lunatic` `Attack 1/2/3`→`Chosen/Chosen/Chosen`;
  `angel` `Protect`→`Protected`; `barista` `Ability twice`→`Acts Twice` (+ two `?` tokens);
  `fibbin` `Used`→`No Ability`; `revolutionary` `Used`→`Register Falsely?` + `Aligned`×2;
  `spiritofivory` `No extra evil`→`No More Evil`; `stormcatcher` `Safe`→`Stormcaught`;
  `deusexfiasco` `Mistake`→`Whoopsie`.
- **Multi-copy** — see the D8 table; the corrected `reminders` arrays are in the §1 JSON patch.

#### §6.1 Tokens the expiry tables still miss

`EXPIRES_AT_DAWN` has 6 entries and `EXPIRES_AT_DUSK` has 10. Cross-referencing every
official ability whose wording is explicitly time-boxed ("until dusk", "tonight", "today",
"tomorrow") against those tables, **24 more tokens are time-boxed but never auto-removed**,
so the storyteller has to clear each one by hand:

Should be in `EXPIRES_AT_DAWN` (placed at night or in the day, consumed by the following
dawn):

| pair | why |
|---|---|
| `undertaker` / `Died Today` | placed on the day's execution, consumed that night; wiki: "Remove the Undertaker's reminder token when convenient" |
| `godfather` / `Died Today` | marks that an Outsider died today, consumed by the Godfather's night kill |
| `zombuul` / `Died Today` | tracks "no-one died today" for tonight's decision |
| `flowergirl` / `Demon Voted`, `Demon Not Voted` | a day fact consumed by tonight's Flowergirl step |
| `towncrier` / `Minion Nominated`, `Minions Not Nominated` | same |
| `juggler` / `Correct` ×5 | placed on day 1, consumed on night 2 |
| `princess` / `Doesn't Kill` | "On your 1st day… the Demon doesn't kill tonight" |
| `barber` / `Haircuts Tonight` | consumed by tonight's Demon swap |
| `hatter` / `Tea Party Tonight` | same |
| `poppygrower` / `Evil Wakes` | the one night the evil team learns each other |
| `po` / `3 Attacks` | cleared once the triple attack happens |
| `toymaker` / `Final Night: No Attack` | re-evaluated every night |
| `acrobat` / `Chosen` | tonight's target only |
| `mezepheles` / `Turns Evil` | consumed the night the player turns (`No Ability` stays) |
| `cacklejack` / `Not Me` | chosen each day, consumed that night |

Should be in `EXPIRES_AT_DUSK` (live through the following day, then gone):

| pair | why |
|---|---|
| `thief` / `Negative Vote` | "their vote counts negatively **tomorrow**" |
| `bureaucrat` / `3 Votes` | "counts as 3 votes **tomorrow**" |
| `barista` / `Sober & Healthy`, `Acts Twice`, `?`, `?` | "Each night, **until dusk**…" |
| `bonecollector` / `Has Ability` | "regain their ability **until dusk**" (the Bone Collector's own `No Ability` is permanent — it marks the once-per-game as spent) |
| `goon` / `Drunk` | "is drunk **until dusk**" |
| `xaan` / `X` | "all Townsfolk are poisoned **until dusk**" |
| `organgrinder` / `Drunk` | re-chosen each night |
| `ventriloquist` / `Mad` | "might not die if executed **today**" |

Two that need their own rule rather than a table entry:

- `courtier` / `Drunk 3` → `Drunk 2` → `Drunk 1` → gone: a **decrement at dusk**, not a
  removal (and per §5.1 the official token order counts *up*, `Drunk 1` first).
- `minstrel` / `Everyone Is Drunk`: "drunk until dusk **tomorrow**" — it must survive one
  dusk and expire at the next.

**On the user's Devil's Advocate report.** `devilsadvocate` / `Survives execution` *is* in
`EXPIRES_AT_DUSK` and does clear correctly on DAY → NIGHT (`GameActions.kt:261`). What the
app cannot do is stop the same player being chosen twice. The official ability is *"Each
night, choose a living player (**different to last night**): if executed tomorrow, they don't
die."* — and because the token is wiped at dusk, the engine keeps no record of last night's
choice, so nothing can enforce the constraint or grey out that seat in the picker. A
`lastChoice: Map<sourceId, List<Long>>` on `GameState`, written whenever a constrained
character resolves and never cleared, is the general fix; the same field serves Fearmonger
(*"All players know if you choose a new player"* — the app must **announce** a change, and
the official otherNightReminder says so explicitly: *"If the target is different to last
night, announce that the Fearmonger has chosen a player."*), Bounty Hunter (*"cannot learn
the same evil player twice"*), and the Riot/Leviathan *"different to previous nights"*
jinxes in §4.

One UI follow-up: `NightScreen.kt:285` renders `items(reminders)`, so a duplicated label now
draws N identical chips. Use `reminders.distinct()` for the chip row and keep
`allReminders.count { it == label }` for the copy budget (`NightScreen.kt:319`), which is
already correct.

## Tests to add

1. **`DataIntegrityTest.everyCodeLabelExistsInData`** — collect every `(sourceId,label)` pair
   from `EXPIRES_AT_DAWN`, `EXPIRES_AT_DUSK` and a new `Reminders` object holding all UI
   literals; Given the bundled `characters.json`, Then every pair's label is in
   `characterById(sourceId).allReminders`. **Fails today** on `marionette` / `Is the Marionette`.
2. **`DataIntegrityTest.reminderLabelsAreTitleCaseAndUnique`** — no two labels in
   `characters.json` differ only by case. **Fails today** (`No ability`/`No Ability`,
   `Has ability`/`Has Ability`).
3. **`DataIntegrityTest.nightListsMatchNightReminders`** — for every character, a non-empty
   `firstNightReminder` ⇔ the id is in `firstNight` (same for other nights). Passes today;
   **will fail** during the `gnome`/`angel`/`buddhist`/`toymaker` fix unless both files are
   edited together — which is the point.
4. **`DataIntegrityTest.guideCoversEveryCharacter`** — every id in `characters.json` has at
   least one guide channel, and `first`/`other` are present exactly when the id is in the
   matching night list. **Fails today** for 55 ids.
5. **`DataIntegrityTest.jinxIdsResolve`** — every `id1`/`id2` in `night_and_jinxes.json`
   resolves to a character. **Will fail** after §4 until Wraith/Cacklejack/Tor are added
   (7 official jinxes reference `wraith`).
6. **`DataIntegrityTest.jinxLookupIsOrderInsensitive`** — Given `mathematician`+`chambermaid`,
   When looked up in either order, Then the same jinx is returned.
7. **`GameActionsTest.innkeeperProtectsTwoPlayers`** — Given Innkeeper, When `Safe` is placed
   on seat A then seat B, Then both hold it; When dawn breaks, Then neither does.
   **Fails today.**
8. **`GameActionsTest.pukkaPoisonThenKillNextNight`** — Given Pukka poisons A on night 2 and B
   on night 3, Then on night 3 A is dead and B is poisoned, and both `Poisoned` tokens existed
   simultaneously at the moment of the night-3 choice. **Fails today** (only one token exists).
9. **`SetupTest.sentinelOffersPlusOrMinusOneOutsider`** — Given a script with Sentinel, Then
   `Setup.allowedDistributions(n, script)` (`Setup.kt:261`) contains `outsiders-1`, `outsiders` and `outsiders+1`. **Fails today**
   (`setup: false`).
10. **`SetupTest.riotHasNoSetupModifier`** — Given Riot, Then `Setup.modifierFor(riot)` is
    null. **Fails today.**
11. **`NightOrderTest.duchessWakesSecond`** and **`riotAndLeviathanAreLastAndAfterDawn`** —
    Given all in play, Then the otherNight index of `duchess` is below every Demon's, `riot`
    is immediately before `DAWN`, and `leviathan` is after it. **Fails today.**
12. **`NightOrderTest.plagueDoctorRunsAfterSweetheart`** — **Fails today** (runs 58 steps early).
13. **`NightOrderTest.gnomeHasNoNightStep`** — **Fails today.**
14. **`GameDataTest.characterCountAndTeams`** — 181 characters; teams
    `townsfolk 69, outsider 23, minion 27, demon 19, traveller 18, fabled 14, loric 11`.
15. **`GameDataTest.charactersJsonMatchesRawShards`** — the concatenation of the four
    `raw_*.json` equals `characters.json`, or the shards are deleted. **Fails today** in
    6 places.
16. **`GameActionsTest.exorcisedPukkaStillKillsLastNightsTarget`** — Given an Exorcist chose
    the Pukka on night 3, Then the player marked `Poisoned` on night 2 still dies at dawn
    (wiki: "Any other Demon abilities still function — such as… the Pukka killing a player
    they attacked on a previous night"). **Fails today**: `NightOrder.kt:149-153` appends
    "the Demon does not act tonight" for every Demon unconditionally.
17. **`GameActionsTest.devilsAdvocateCannotRepeatLastNightsChoice`** — Given the Devil's
    Advocate chose seat A on night 2, When night 3's picker is built, Then seat A is not
    offered. **Fails today** — `GameState` retains no previous choice once the token expires.
18. **`GameActionsTest.witchCurseClearsAtThreeAlive`** — Given a `Cursed` token and a
    mid-night death leaving 3 alive, Then the token is removed immediately, not at dusk.
    **Fails today.**
19. **`NightOrderTest.marionetteSkipsMinionConfirmingSteps`** — Given a Marionette, Then it
    is excluded from `MINION_INFO`, `snitch`, `preacher`, `lilmonsta`, `poppygrower`,
    `hatter` and `damsel`. **Fails today** — only `MINION_INFO` skips it
    (`NightOrder.kt:60-95`).
20. **`NightOrderTest.hermitWakesAtEveryOutsiderStep`** — see D10b. **Fails today.**
