# Bundled character data — where it comes from and what we change

WP5. Everything the app knows about characters, jinxes, the night order and the
storyteller run-book lives in four places:

| file | generated? | by |
|---|---|---|
| `engine/src/main/resources/botc/data/characters.json` | **yes**, wholesale | `tools/regen-data.py` |
| `engine/src/main/resources/botc/data/night_and_jinxes.json` | **yes**, wholesale | `tools/regen-data.py` |
| `engine/src/main/resources/botc/data/night_guide.json` | hand-written, patched | `tools/patch-night-guide.py` |
| `tools/app-overlay.json` | hand-edited | — (seedable with `--bootstrap-overlay`) |

Do not hand-edit the two generated files. Change the overlay or the script and
re-run; a stray manual edit is silently overwritten on the next regeneration.

## The pipeline

```
tools/data/roles.json      ─┐
tools/data/jinxes.json      ├─► tools/regen-data.py ─► characters.json
tools/data/nightsheet.json ─┘         ▲                night_and_jinxes.json
                                      │
                            tools/app-overlay.json
```

```
night_guide.json ─► tools/patch-night-guide.py ─► night_guide.json   (idempotent)
```

    python3 tools/regen-data.py            # regenerate both data files + validate
    python3 tools/regen-data.py --check    # validate only
    python3 tools/regen-data.py --fetch    # re-vendor from upstream, then regenerate
    python3 tools/patch-night-guide.py     # (re-)apply the guide corrections
    python3 tools/patch-night-guide.py --check

Both scripts are Python 3 with no dependencies, and neither the build nor the
app ever touches the network: the official files are vendored.

## Source of truth

Lead decision **D31**: The Pandemonium Institute's own machine-readable data —
the same files the official app and the Script Tool ship.

* Repo: <https://github.com/ThePandemoniumInstitute/botc-release>, `resources/data/`
* Pinned commit: `915347e627c3f6cd1f438f82b6001784e11b3e8b` (2026-07-08,
  "Update Magician/Mathematician jinxes"). Recorded in `tools/data/SOURCE.json`.
* `roles.json` 181 characters · `jinxes.json` 131 jinxes (grouped by owner) ·
  `nightsheet.json` firstNight 80 / otherNight 99.

Everything in those files wins over the old bundle, including where the
audit's own transcription differs — the two `jinxes.json` reasons that carry a
trailing space (`legion`×`summoner`, `lilmonsta`×`magician`) are the only text
the pipeline touches, and only to strip that space.

## What the overlay adds

`tools/app-overlay.json` is the app's half of the merge. Two kinds of entry:

* **`firstNightReminder` / `otherNightReminder`** for 106 characters. The
  official strings are terse ("Give a finger signal."); the app ships
  bra1n/townsquare's verbose storyteller prose, and data-accuracy P3 #18 rules
  that the app's copy is better and must be kept. Any character not listed
  here takes the official string.
* **`spentLabel`** for 23 characters (lead **D49**, ARCHITECTURE §2.14) — the
  exact reminder label that marks a once-per-game ability as used. This drives
  `Gates.notSpent`; the "Once per game" text heuristic is deleted.
* **`reminders`** — per-character deltas ON TOP of the official reminder
  lists: extra copies of a label, extra labels, labels reclassified as global.
  Added in Wave 6C; every entry carries its own `why` and is listed in
  §"Wave 6C" below.
* **`nightOrder`** — the four rows the official `nightsheet.json` omits, each
  anchored to the row it follows. Added in Wave 6C; see §"Wave 6C".

`manualOverrides` (currently empty) is a raw field-level escape hatch applied
after the official data; anything put there must be justified in this file.
Prefer `reminders`, which records a delta rather than a replacement, so an
upstream change to the official list still flows through.

### `spentLabel` assignments

`No Ability` — artist, assassin, bonecollector, courtier, engineer, fibbin,
fisherman, fool, huntsman, judge, mezepheles, nightwatchman, professor,
seamstress, slayer, **summoner**, virgin.
`Guess Used` — damsel, puzzlemaster. `May Not Nominate` — golem.
`Is The Philosopher` — philosopher. `Woke` — **sage**.
`Wish Granted` — **wizard**.

Deliberately **not** set: `preacher` (its three `No Ability` tokens go on
Minions, not on the Preacher), `pixie`, `plaguedoctor`, `deusexfiasco`,
`doomsayer`, `fiddler`, `revolutionary`, `toymaker`, `knaves`, `zombuul`,
`gangster`, `gunslinger`, `psychopath` (the last three are once per *day*).
`philosopher` is a judgement call: the official data has no spent token, but
the `Is The Philosopher` global reminder is placed exactly when the
once-per-game ability is used.

## Manual overrides — the complete list

Every deviation from "official data verbatim", and why.

### 1. `team` for the 11 Loric characters → `"fabled"` (temporary)

ARCHITECTURE §2.14 adds `@SerialName("loric") LORIC` to `Team`, but
`Character.kt` is WP0's file, not WP5's. kotlinx.serialization throws on an
unknown enum value (`ignoreUnknownKeys` forgives unknown *keys* only), so
shipping `"loric"` before WP0 lands makes the entire dataset fail to load and
every engine test fail.

`regen-data.py` therefore **auto-detects**: it greps `Character.kt` for
`@SerialName("loric")` and writes `"loric"` if it is there, `"fabled"` if not.
**Once WP0 lands, re-run `python3 tools/regen-data.py`** and the 11 entries
flip with no other change. `--loric-team loric|fabled` forces either.

Affected: `bigwig`, `bootlegger`, `gardener`, `godofug`, `hindu`, `knaves`,
`pope`, `stormcatcher`, `tor`, `ventriloquist`, `zenomancer`. Their `edition`
is already the truthful `"loric"` regardless of the compat, so the flip is one
field.

### 2. `edition` ids

* `carousel` → `exp` and `snv` → `sv` (the app's ids; data-accuracy P3 #20).
* Every Fabled-team character is filed under edition `fabled` and every Loric
  under `loric`, following the app's existing grouping. Official `roles.json`
  files `deusexfiasco` and `ferryman` under `carousel`; putting them in `exp`
  would leak them into `GameData.charactersOf("exp")` and the traveller
  matching in `GameData.travellersFor`.

### 3. `team` spelling

Official `traveller` → `"traveler"`, matching `Team`'s existing
`@SerialName("traveler")`.

### 4. Night-reminder prose

The app's verbose prose is kept for the 106 characters in the overlay
(data-accuracy P3 #18). Five characters take the **official** string instead,
because the app's was wrong rather than merely verbose:

| id | field | why |
|---|---|---|
| `stormcatcher` | firstNightReminder | the app described a completely different ability (a `Safe` token that collided with the Monk's, and no "character is not in play" branch) |
| `angel` | firstNightReminder | app had `""`; official announces the protection, and the Angel is in the official firstNight order |
| `buddhist` | firstNightReminder | same |
| `toymaker` | firstNightReminder | same — its mandatory job is "resolve Minion Info and Demon Info even under 7 players" |
| `gnome` | firstNightReminder | official is `""`: the Gnome has **no** night action, so the app's invented step is gone and its run-book moved to the guide's `day` channel |

### 5. Official prose markup

Official night strings carry two markup forms. `*TOKEN NAME*` (an info token
to show) is meaningful to a storyteller and is **kept** — see `cacklejack`,
`stormcatcher`, `tor`. `:reminder:` is a rendering hint for TPI's own UI with
no meaning in prose and is **stripped** (`angel` only). Runs of whitespace are
collapsed. Applies only to official-sourced strings; overlay prose is
untouched.

### 6. Jinx reason text

Trailing whitespace stripped. Two entries in `jinxes.json` have it:
`legion`×`summoner` and `lilmonsta`×`magician`. Nothing else is edited.

### 7. `id1` / `id2` ordering in jinxes

`id1` is the official group owner. The old bundle stored 21 pairs the other way
round, so anything that renders or sorts by `id1` will reorder.
`GameData.activeJinxes` already matches order-insensitively and needs no change,
but any future "does this exact pair have a jinx?" helper **must** normalise the
pair.

### 8. Output ordering and formatting

Both files are pretty-printed with one-space indent and a trailing newline, the
shape the existing files already had. `characters.json` is grouped by edition
(`tb, bmr, sv, exp, fabled, loric`), then team (`townsfolk, outsider, minion,
demon, traveler, fabled, loric`), then id — as before, except that
`deusexfiasco` (previously appended at the end of the Fabled block) now sorts
into place. Field order: `id, name, edition, team, ability, setup,
firstNightReminder, otherNightReminder, reminders, remindersGlobal,
spentLabel`; `spentLabel` is emitted only when non-empty.

### 9. `raw_*.json` deleted

`raw_tb_bmr.json`, `raw_sv_travellers_fabled.json`, `raw_exp_townsfolk.json`
and `raw_exp_evil_outsiders.json` (~80 KB) are gone. Nothing referenced them
(`grep -rn raw_ engine app web tools .github` is empty), they had already
diverged from `characters.json` in six places, and `.github/workflows/build.yml`
was shipping and service-worker-caching all four to the PWA. `characters.json`
is now generated from the official data, which makes them redundant.

This resolves data-accuracy P1 #12 and P3 #19 in favour of deletion (the audit
offers "delete them **or** make `characters.json` a generated artifact" and we
now do both). **Note for the lead:** D28 asks for a
`characters.json` ↔ `raw_*.json` parity test; D31 supersedes it — the parity
test that matters now is `characters.json` ↔ `tools/data/roles.json`, which
`regen-data.py --check` performs and which WP12 should also pin in Kotlin.

## What the regeneration fixed

Counts, after: **181 characters** (was 171), **131 jinxes** (was 58),
**80 / 99** night-order entries (was 76 / 96) before Wave 6C's four insertions,
**185 night-guide entries** (was 116 entries, 166 night channels, 119 show cards).

* 10 characters added: `wraith` (Minion), `cacklejack` (Traveller), and the
  eight Loric `bigwig`, `godofug`, `hindu`, `knaves`, `pope`, `tor`,
  `ventriloquist`, `zenomancer`.
* 80 jinxes added, 39 stale texts replaced, **7 retired jinxes removed** —
  `kazali`×`choirboy`, `marionette`×`damsel`, `lycanthrope`×`gambler`,
  `marionette`×`poppygrower`, **`marionette`×`snitch`** (lead D38),
  `summoner`×`poisoner`, `riot`×`saint`.
* Setup flags corrected: `riot` true→**false**, `sentinel` false→**true**,
  `bootlegger` false→**true**, `gardener` false→**true**,
  `deusexfiasco` false→**true**.
* 23 characters now list a multi-copy reminder the required number of times
  (`pukka` `Poisoned`×2, `po` `Dead`×3, `shabaloth` `Dead`×2,
  `vigormortis` `Has Ability`×3 + `Poisoned`×3, `juggler` `Correct`×5,
  `mathematician` `Abnormal`×5, `tealady` `Cannot Die`×2, `innkeeper`
  `Safe`×2, `nodashii` `Poisoned`×2, `harlot` `Dead`×2, `angel`
  `Protected`×2, `duchess` `Visitor`×2, `lunatic` `Chosen`×3, …).
* Every reminder label is official Title Case; **no two labels differ only by
  case** any more (89 distinct labels).
* Renamed tokens the code must follow (data-accuracy §6):
  `innkeeper` `Protected`→`Safe`; `tealady` `Can not die`→`Cannot Die`;
  `scarletwoman` `Demon`→`Is The Demon`; `lunatic` `Attack 1/2/3`→`Chosen`×3;
  `angel` `Protect`→`Protected`; `barista` `Ability twice`→`Acts Twice`;
  `fibbin` `Used`→`No Ability`; `revolutionary` `Used`→`Register Falsely?` +
  `Aligned`×2; `spiritofivory` `No extra evil`→`No More Evil`;
  `stormcatcher` `Safe`→`Stormcaught`; `deusexfiasco` `Mistake`→`Whoopsie`;
  `Red herring`→`Red Herring`; `No ability`→`No Ability`;
  `Has ability`→`Has Ability`; `Died today`→`Died Today`;
  `Survives execution`→`Survives Execution`; `Is the Drunk`→`Is The Drunk`;
  `Is the Philosopher`→`Is The Philosopher` (and it moved to
  `remindersGlobal`); `Is the Marionette` was already `Is The Marionette` in
  the data — it is `GameShell.kt` that is wrong.
* Night order rebuilt from `nightsheet.json`: `duchess` now wakes second on
  other nights, `plaguedoctor` runs after `sweetheart` instead of 58 steps
  early, `riot` sits immediately before `DAWN` and `leviathan` after it,
  `courtier` precedes `innkeeper`, `harlot` precedes `bonecollector`,
  `stormcatcher` resolves before `lordoftyphon`, and `gnome` is gone.

### `night_guide.json`

`tools/patch-night-guide.py` applies data-accuracy §5.1's 136 defects (30 P0,
51 P1, 55 P2) plus the `data:` corrections from the digest cards. It is
idempotent — every operation is a full replacement or is skipped once its
result is present — so it can be re-run after a hand edit.

* The four P0 sentences are gone and the script asserts it: `undertaker`
  ("even if they did not die from it"), `vortox` ("no one dies, but
  information is still false" — lead D11), `shabaloth` ("their once-per-game
  abilities remain as they were"), `butler` ("This still applies even if the
  Butler is drunk or poisoned").
* **Marker entries** `DUSK`, `MINION_INFO`, `DEMON_INFO`, `DAWN` (lead D23) —
  the dusk/dawn token sweeps, the Poppy Grower / Magician / Marionette /
  Summoner / Lil' Monsta / Legion / Tor variations of the two info steps, and
  the dawn announcements the rules require. `MINION_BLUFFS` from ARCHITECTURE
  §4 WP5.6 is **not** added: it is not a `NightMarkers` id, and the Snitch's
  per-Minion bluffs are covered inside `DEMON_INFO` and `snitch.setup`.
* **New channels** (ARCHITECTURE §2.14, data-accuracy §5.2): `setup` 36,
  `day` 41, `reference` 31. Every one of the 181 characters now has at least
  one channel, and `first`/`other` exist **iff** the id is in the matching
  night-order list. The Kotlin `NightGuideEntry` still has only `first`/`other`
  and the parser uses `ignoreUnknownKeys`, so the app loads this file unchanged
  until WP0 adds the three fields.
* `gnome`'s `first` entry moved to `day` and was rewritten (the Storyteller may
  not prompt the Gnome; the kill happens before voting starts; the vote for the
  nominee still happens).

**One genuine source conflict, flagged rather than silently resolved:** for the
Storm Catcher, `roles.json` says the reminder is `Stormcaught` and the
not-in-play branch shows `THESE CHARACTERS ARE NOT IN PLAY`, while the wiki's
older How to Run still says a `SAFE` reminder and a `THIS PLAYER IS` token.
`roles.json` is the newer source and matches the reclassification to Loric, so
the data and the guide follow it; the guide entry says so out loud so a
storyteller who checks the wiki is not confused.

## Invariants the scripts enforce

`regen-data.py --check` fails on any of:

* not exactly 181 characters / 131 jinxes / 81 firstNight / 102 otherNight
  (80 + 1 and 99 + 3 overlay insertions);
* a duplicate or non-normalised id;
* a night-order or jinx id that does not resolve to a character;
* a character with a night reminder that is not in the matching order list, or
  in the list without a reminder (both directions);
* a `spentLabel` that is not one of that character's own reminders;
* two reminder labels that differ only by case;
* a character with no `night_guide.json` channel, a `first`/`other` channel
  that disagrees with the night order, or a missing marker entry;
* an overlay `reminders` entry that changes nothing (the official data has
  caught up — delete the entry) or names a label the official list does not
  carry, and a `nightOrder` insertion whose id the official sheet now has or
  whose anchor does not exist.

`patch-night-guide.py --check` additionally fails on a `GuideShow` whose `kind`
is outside `NightGuide.VALID_KINDS` or whose `token` is outside
`VALID_TOKENS`, on a blank `instructions`, and on any of the four P0 sentences
reappearing.

## Known-failing engine tests (for WP12) — all three now fixed

Historical, kept for the record. All three were resolved in later waves; as of
Wave 6C the engine suite is 759 tests, 0 failures, 6 skipped.

These three failed **because** the data is now correct. None was a load failure —
the dataset parses and 102 of 105 tests passed at the time.

1. `SetupTest > team warping brackets relax all counts` — asserts
   `Setup.modifierFor(riot) != null`; `riot.setup` is now `false`, which is what
   lead D28 and data-accuracy test #10 (`riotHasNoSetupModifier`) demand. Riot
   must come out of `TEAM_WARPING_IDS` and be replaced by an explicit BagShape.
2. `NightGuideTest > guide covers every night actor with valid shows` —
   asserts every `night_guide.json` key resolves to a character; the four
   marker entries mandated by D23 do not. Every other assertion in that test
   passes (verified: no unresolvable non-marker key, the Pixie's pick card is
   intact, no night-order id lacks an entry, no blank instructions, no invalid
   show kind or token).
3. `FullGamePlaytestTest > 15 player Bad Moon Rising complete game including
   Mastermind day` — the fixture pins the night-2 sheet as
   `… innkeeper, courtier …`; the official order is `courtier` then
   `innkeeper`. Swapping those two rows in the fixture is the whole fix.

## Refreshing from upstream later

    python3 tools/regen-data.py --fetch
    python3 tools/patch-night-guide.py --check
    ./gradlew :engine:test

`--fetch` re-downloads the three files, rewrites `tools/data/SOURCE.json` with
the new upstream commit, and regenerates. Read the diff: an upstream change to
a reminder label is a code change too (`GameActions.EXPIRES_AT_*`, the token
rules), and an upstream change to the night order will move steps under the
storyteller's feet.

## Wave 6C — the registry agents' data issues

The nine Wave 4 registry packages each ended with a list of things
`characters.json` could not say. `docs/audit/FOLLOWUPS.md` §"From Wave 4
registry agents" collects them; this section is what was done about each.

Nothing here is a guess. Every item was re-checked against the character's own
wiki page before it was applied, and one was rejected on that check (see
"Rejected", below).

### 1. Four night-order rows the official sheet omits

`tools/app-overlay.json` → `nightOrder`. `nightsheet.json` stays verbatim
truth for every row it carries and is never reordered; these four are inserted
after a named anchor, and each carries its own `why` in the overlay.

| list | id | after | why |
|---|---|---|---|
| otherNight | `widow` | `poisoner` | "On your 1st night" means the night the Widow **enters play**. The wiki's own example is a Pit-Hag who becomes the Widow on night 3 and sees the Grimoire, poisons and wakes the KNOW player that night. Same slot as on the first night. |
| otherNight | `snitch` | `scarletwoman` | A Snitch, or a Minion, created mid-game still owes three bluffs. Placed before `summoner`/`lunatic`, mirroring its first-night place straight after MINION INFO. |
| otherNight | `ogre` | `spy` | "On your 1st night" again. Same neighbours as on the first night — after the Spy, before the High Priestess. |
| firstNight | `plaguedoctor` | `pixie` | The safety net for a Plague Doctor who dies before the first night ends (an Angel's "something bad", a storyteller death). Nobody is dead on an ordinary night 1, and the registry gate — "dead, and no ability taken yet" — skips the row then. |

Each row needed a night reminder (the generator fails without one) and a
`night_guide.json` channel (both validators fail without one); the reminders
are in the overlay's `characters` block and the run-books are
`patch-night-guide.py` §9.

Totals move from **80 / 99** to **81 / 102**.

### 2. Nineteen reminder deltas

`tools/app-overlay.json` → `reminders`, applied by `apply_reminder_delta()`.
Four operations — `copies`, `add`, `addGlobal`, `toGlobal` — all of them
**additive**: the generator never drops or renames an official label, and
`DataParityTest.appReminders` re-asserts that from the Kotlin side,
allow-listing exactly these ids and no others.

**Copy counts the rules must be able to reach**

| id | label | 1 → | why |
|---|---|---|---|
| `leviathan` | Good Player Executed | 2 | "If **more than 1** good player is executed, evil wins." With one copy the second mark displaced the first and `goodExecutedMarks` could never return 2. |
| `widow` | Poisoned | 2 | The poison lasts until the Widow dies, so two Widows hold two victims at once. |
| `snakecharmer` | Poisoned | 2 | Each swap poisons the new Snake Charmer permanently; a second swap must not cure the first victim. |
| `sweetheart` | Drunk | 2 | "1 player is drunk from now on", per Sweetheart death and permanent. |

**Labels for a state the wiki names and the official token set does not**

| id | label(s) | why |
|---|---|---|
| `angel` | No Ability, Can't Vote | The wiki's SOMETHING BAD token stands for "poisoned, or mad, or can't vote today". Poisoned and Mad are generic storyteller tokens; the other two had no label, so the penalty could not be placed, read or swept. Both `Until.DUSK`. |
| `hellslibrarian` | No Ability, No Vote | The same sentence on the Hell's Librarian's page. |
| `beggar` | Token | "If a dead player gives you their vote token, you learn their alignment" — the donation has to be visible for the rest of the game. |
| `boomdandy` | Exploded | "Declare that the Boomdandy has exploded": the explosion runs across a ring of kills, a countdown and a finger vote, so the state must survive between taps. Grimoire centre. |
| `buddhist` | Silent ×3 | "Declare which players are Buddhists" — the veterans must be visible for the two silent minutes of each day. Three copies because more than one is normally named. **Never** an impairment. |
| `doomsayer` | Used | The grimoire's copy of `FabledEntry.spentBy`, which stays the authority. |
| `gunslinger` | No Ability | Once per **day**, so no `spentLabel` (D49); swept at dawn. |
| `lleech` | Host | The official Soldier ruling has a host who is **not** poisoned. Until now the only marker was the poison itself, so host-ness and impairment could not be told apart. `hostOf` reads `Host` first and keeps `Poisoned` as a fallback for older saves. |
| `psychopath` | Used Today | Once per **day**; the `STATEMENT` ledger row stays the authority and the token closes the window from the other side. |
| `sage` | Woke | `NightEffect.MarkSpent` was writing a **labelless** spent effect, invisible in the grimoire. Also the new `spentLabel`. |
| `summoner` | No Ability | The summon removes the Night 3 token, so nothing was left saying the Summoner is finished. Also the new `spentLabel`. |
| `vizier` | No Ability | The Courtier/Preacher jinx — "if the Vizier loses their ability, they learn this" — is a state the table can see. It carries no `NO_ABILITY` effect: whatever stripped the Vizier owns that. |
| `wizard` | Wish Granted | A **declined** wish is not a spend, and the two `?` tokens track the wish's ongoing effects rather than the spend. Also the new `spentLabel`, replacing the "deliberately not set" note above. |

**Reclassified as global**

| id | label | why |
|---|---|---|
| `boffin` | Demon Has This Ability | The wiki runs the Boffin by laying a **second character token** next to the Demon's. The grimoire has one token per seat, so the grant is a global reminder naming a character that is not in play — which is what `remindersGlobal` is for. The granted id goes in `PlacedReminder.characterId`. |
| `minstrel` | Everyone Is Drunk | Moved out of `reminders`: the token is a fact about the whole table, not about the seat it is drawn on (D9/D15 — `grimoireCentre`). This reverses WP5's earlier "stays in `reminders`" note, at WP7-BMR's request. `allReminders` is unchanged, so no rule moved and its `DUSK_AFTER_N_DAYS` lifetime is untouched. |

Every label above is official **Title Case** (lead D5). FOLLOWUPS spells four
of them in sentence case (`No ability`, `Used today`, `Demon has this
ability`, `Can't vote`); shipping those verbatim would have collided with the
existing `No Ability` under the generator's own "no two labels differ only by
case" invariant, so they are Title Case here.

### 3. Prose fixes

All in `tools/app-overlay.json` → `characters`.

| id | field | change |
|---|---|---|
| `ogre` | firstNightReminder | Was "The Ogre chooses a player." Now says **not themself**, names the Friend token, states the alignment flip, that the Ogre is never told, and that it works while drunk or poisoned. |
| `ogre` | otherNightReminder | New — the same step for an Ogre who has just entered play. |
| `widow` | otherNightReminder | New — the whole first-night step, for a Widow who has just entered play. |
| `snitch` | otherNightReminder | New — bluffs for a Minion who has not been given a set yet. |
| `plaguedoctor` | firstNightReminder | New — the same catch-up sentence as the other-night one. |
| `alhadikhia` | otherNightReminder | Now says **dead players are legal targets**, that a player who chooses to live has their shroud removed (so a dead one is alive again), and that if all three live, all three die. |
| `towncrier` | otherNightReminder | "Minion not nominated" / "Minion nominated" → the official labels **Minions Not Nominated** / **Minion Nominated**. |
| `seamstress` | both | Stray space before the comma in "chose players ,". |
| `philosopher` | both | "Is the Philosopher" → **Is The Philosopher**, the official label the prose tells the storyteller to place. `spentLabel` stays `Is The Philosopher` — the wiki gives the Philosopher no spent token, and that global reminder is placed exactly when the once-per-game ability is used. |

### 4. `night_guide.json`

`patch-night-guide.py` §9, all `setdefault` so re-running is still a no-op.

* `widow.other`, `ogre.other`, `snitch.other`, `plaguedoctor.first` — required
  by both validators for the four new order rows.
* The channels WP7-EXP-O reported missing: `hermit.day` (the Outsider
  abilities the Hermit holds that act in daylight), `golem.reference` (the
  Golem's kill is not an execution), `puzzlemaster.setup` (choose and mark the
  drunk player before night 1 — the one genuinely missing setup step),
  `politician.day`, `zealot.day`, `heretic.day`.

FOLLOWUPS asks for an "end" channel for the last three. There is no such
channel: `NightGuideEntry` (ARCHITECTURE §2.14) has `first`, `other`, `setup`,
`day`, `reference`, and adding a sixth is a Kotlin schema change, not a data
one. End-of-game work therefore lives in `day`, which is where the storyteller
is standing when it happens.

Channels move from 287 to **297**, show cards from 136 to **137**.

### Rejected

* **`bigwig` reminders.** FOLLOWUPS lists the Big Wig among the characters
  with an empty reminder set. It is empty in `roles.json`, it is empty in the
  audit's own transcription (`mechanics/data-accuracy.md`), and the wiki's How
  to Run names no token either — the whole ability is "ask the nominee to pick
  a player; that player is the only one who may speak until the vote". There
  is nothing to add, so nothing was added; `RulesFabled.bigWig` keeps
  expressing the madness as a nomination trigger.

### Not a data change

* **`lycanthrope` Faux Paw `characterId`.** WP7-EXP-T asked whether the token
  could carry its payload. `PlacedReminder.characterId` and
  `Effect.characterId` both exist, and `RulesExpTownsfolk`'s standing rule
  already emits `REGISTERS_AS` with `characterId = "evil"` off the token's
  presence. What is missing is a **placer**: `Faux Paw` is put down by hand at
  setup, and giving it a payload needs `NightEffect.PlaceToken` to carry
  `characterId` (a WP2 schema gap, filed by five packages) or a
  `lycanthrope.fauxpaw` `SetupRequirement`. Neither is a data file, so
  `characters.json` is unchanged — `Faux Paw` was already an official reminder.

### What a future upstream refresh must check

`regen-data.py --check` now fails if an overlay `reminders` entry has become a
no-op or a `nightOrder` insertion has been overtaken by the official sheet, so
`--fetch` will tell you when TPI adds one of these itself. When it does, delete
the overlay entry and the matching row from `DataParityTest.appReminders`.
