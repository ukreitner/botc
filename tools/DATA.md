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
* **`spentLabel`** for 20 characters (lead **D49**, ARCHITECTURE §2.14) — the
  exact reminder label that marks a once-per-game ability as used. This drives
  `Gates.notSpent`; the "Once per game" text heuristic is deleted.

`manualOverrides` (currently empty) is a field-level escape hatch applied after
the official data; anything put there must be justified in this file.

### `spentLabel` assignments

`No Ability` — artist, assassin, bonecollector, courtier, engineer, fibbin,
fisherman, fool, huntsman, judge, mezepheles, nightwatchman, professor,
seamstress, slayer, virgin.
`Guess Used` — damsel, puzzlemaster. `May Not Nominate` — golem.
`Is The Philosopher` — philosopher.

Deliberately **not** set: `preacher` (its three `No Ability` tokens go on
Minions, not on the Preacher), `wizard` (`?` tokens track the wish, not the
spend), `pixie`, `plaguedoctor`, `deusexfiasco`, `doomsayer`, `fiddler`,
`revolutionary`, `toymaker`, `knaves`, `zombuul`, `gangster` (once per *day*).
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
**80 / 99** night-order entries (was 76 / 96), **185 night-guide entries** with
**287 channels** and **136 show cards** (was 116 entries, 166 night channels, 119 show cards).

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

* not exactly 181 characters / 131 jinxes / 80 firstNight / 99 otherNight;
* a duplicate or non-normalised id;
* a night-order or jinx id that does not resolve to a character;
* a character with a night reminder that is not in the matching order list, or
  in the list without a reminder (both directions);
* a `spentLabel` that is not one of that character's own reminders;
* two reminder labels that differ only by case;
* a character with no `night_guide.json` channel, a `first`/`other` channel
  that disagrees with the night order, or a missing marker entry.

`patch-night-guide.py --check` additionally fails on a `GuideShow` whose `kind`
is outside `NightGuide.VALID_KINDS` or whose `token` is outside
`VALID_TOKENS`, on a blank `instructions`, and on any of the four P0 sentences
reappearing.

## Known-failing engine tests (for WP12)

These three fail **because** the data is now correct. None is a load failure —
the dataset parses and 102 of 105 tests pass.

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
