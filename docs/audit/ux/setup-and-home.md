# Setup & Home (ux/setup-and-home) — getting a game started, and the reference material

Scope: `HomeScreen.kt`, `SetupScreen.kt`, `BluffsSheet.kt`, `RevealFlow.kt`,
`GameExtras.FabledSheet` / `RevealSheet` / `GameLogDialog` / `ReorderSeatsDialog`,
`LibraryScreen.kt`, `ReferenceScreen.kt`, script import
(`Script.kt` / `ScriptLink.kt` / platform file pickers), `MainActivity` +
`web/.../Main.kt` navigation, `UpdateBanner.kt`, and the PWA shell
(`web/src/wasmJsMain/resources/{index.html,manifest.webmanifest,sw.js}`).

I own the **interaction design**. Rules correctness for individual characters is
owned by `docs/audit/characters/*` and `docs/audit/mechanics/setup-and-identity.md`;
rules are quoted here only where they define *what the UI must ask the
storyteller for*.

The single sentence this document argues:

> **The app treats "start a game" as three forms to fill in, when it is
> actually a checklist of ~20 storyteller decisions plus a physical ritual
> (passing the phone round the table). It automates 4 of the 20 decisions and
> none of the ritual.**

---

## Official rules (sources)

### 1. The setup-time decision list (what a storyteller actually owes the game before night 1)

Every one of these is a decision the storyteller must make and *record* before
or during the first night. Sources are the current wiki pages plus the app's own
bundled `night_guide.json` (which is already correct prose — it just isn't wired
to any input).

| # | Trigger in play | Storyteller must decide / record | Source |
|---|---|---|---|
| 1 | Fortune Teller | one **good** player is the Red Herring, marked all game | wiki `Fortune_Teller`; `night_guide.json` "fortuneteller".first: *"Before the game, secretly choose a good player as the Red Herring and mark them with the Red Herring reminder"* |
| 2 | Drunk | which not-in-play **Townsfolk** token the Drunk sees | wiki `Drunk` |
| 3 | Lunatic | which **Demon** token the Lunatic sees | wiki `Lunatic` |
| 4 | Lunatic | **which players it is told are its "Minions"** (as many as there are real Minions; may be any players) | wiki `Lunatic` How to Run; `night_guide.json` "lunatic".first |
| 5 | Lunatic | **its own 3 bluffs** — a *separate* set from the Demon's, and they *may include characters that are actually in play* | wiki `Lunatic`: *"Shown three bluff character tokens (can include characters actually in play)"* |
| 6 | Marionette | which good token it sees; must neighbour the Demon | wiki `Marionette` |
| 7 | Evil Twin | which **opposing-alignment** player is the twin; TWIN reminder on the good twin | wiki `Evil_Twin`: *"The Storyteller chooses a good character to be marked with the TWIN reminder during game setup"*; *"If both twins end up with the same alignment, the Storyteller chooses a new Twin"* |
| 8 | Snitch | **3 not-in-play bluffs for EACH Minion** — *"Each Minion may learn the same three characters or different ones"* — shown **before** the Demon gets its bluffs | wiki `Snitch`; `night_guide.json` "snitch".first: *"Before the Demon receives its bluffs, wake each Minion one at a time…"* |
| 9 | Bounty Hunter | **1 Townsfolk is evil** (an alignment flip at setup) and which evil player the BH is shown | wiki `Bounty_Hunter`; `characters.json` `bountyhunter` `setup:true`, reminder `Know` |
| 10 | Pixie | which **in-play Townsfolk** the Pixie is mad about; MAD reminder | `night_guide.json` "pixie".first |
| 11 | Village Idiot ×2–3 | **which extra copy is drunk**; DRUNK reminder | `night_guide.json` "villageidiot".first |
| 12 | Alchemist | which **not-in-play Minion** ability the Alchemist holds | `night_guide.json` "alchemist".first: *"a not-in-play Minion you chose during setup"* |
| 13 | Amnesiac | the secret ability, chosen at setup | `night_guide.json` "amnesiac".first: *"You secretly decided the Amnesiac's ability during setup"* |
| 14 | Xaan | the value of **X** | `night_guide.json` "xaan".first: *"You secretly chose X during setup"* |
| 15 | Lord of Typhon | which two neighbours **became Minions at setup**, and their characters | `night_guide.json` "lordoftyphon".first |
| 16 | Kazali | the bag contains **no Minions**; the Kazali creates them on night 1 | `night_guide.json` "kazali".first: *"There are no pre-set Minions - the Kazali creates them now"* |
| 17 | Lil' Monsta | **no Demon is dealt to a seat** (`[+1 Minion]`); Lil' Monsta is a token a Minion babysits | `night_guide.json` "lilmonsta".first: *"Lil' Monsta is a token, not a player"* |
| 18 | Godfather / Hermit / Balloonist / Xaan / Kazali / Lord of Typhon | **which of the bracket's legal Outsider counts** you are actually playing | `characters.json` brackets `[-1 or +1 Outsider]`, `[-0 or -1 Outsider]`, `[+0 or +1 Outsider]`, `[X Outsiders]`, `[-? to +? Outsiders]` |
| 19 | Huntsman / Choirboy | the companion (Damsel / King) is forced into the bag | `characters.json` `[+the Damsel]`, `[+the King]`; `Setup.COMPANIONS` |
| 20 | Poppy Grower | Minion Info and Demon Info are **skipped**; the Demon is woken alone for bluffs | `night_guide.json` "poppygrower".first |
| 21 | Any Traveller | their **alignment** (storyteller's choice) and the fact they do **not** count toward the bag distribution | wiki `Travellers` |
| 22 | Any Fabled | which Fabled are in play — **before** the bag is built (the Sentinel changes the legal Outsider count) | wiki `Sentinel` / `Fabled` |
| 23 | Every game | the Demon's 3 not-in-play good bluffs | wiki `Demon` / first-night order |

**The app prompts for 4 of these 23** (rows 1, 2, 3, 6) and validates the same 4
(`GameActions.validateSetupState`, `GameActions.kt:503-561`).

### 2. Rules that constrain the *reveal* ritual

- **Ogre** — wiki `Ogre`: *"On your 1st night, choose a player (not yourself):
  you become their alignment **(you don't know which)**"*, and the How-to-Run is
  explicit: *"The Ogre does **not** learn their alignment at game start… The Ogre
  is **not told** whether they are evil."* Therefore **any screen shown to a
  player must never colour their card by the storyteller's alignment override.**
- **Evil Twin** — the good twin *"does not learn which of them is evil beyond
  this"* (`night_guide.json` "eviltwin".first). The reveal must show the twin's
  *character token*, not an alignment.
- **Lunatic** — the Lunatic's "YOU ARE" card is the **Demon token they believe
  in**, red is correct there; the *real* Demon must additionally be shown *"This
  player is the Lunatic"*.
- **Travellers** — a traveller must be told their alignment; a Townsfolk must
  not be told anything beyond their token.

Sources:
`https://wiki.bloodontheclocktower.com/Snitch`,
`https://wiki.bloodontheclocktower.com/Lunatic`,
`https://wiki.bloodontheclocktower.com/Evil_Twin`,
`https://wiki.bloodontheclocktower.com/Ogre`,
plus `engine/src/main/resources/botc/data/night_guide.json` and
`engine/src/main/resources/botc/data/characters.json` (which agree with the wiki
on every point quoted above).

---

## What the app does today

### Home (`HomeScreen.kt`)

Works: a good-looking landing page (drawn night sky, `HomeScreen.kt:72-117`) with
Resume game / New game / Player notes / Library / End game, and a `build <sha>`
footer (`:229-236`). Resume shows script · player count · phase (`:174-184`).
End game is confirmed (`:239-249`).

Everything else about it is a single-slot design: `SavedData` holds exactly one
`game`, one `notes`, and the imported scripts (`data/SavedData.kt:9-15`).
`endGame()` nulls the game and writes through immediately
(`GameViewModel.kt:89-98`, `WebGameViewModel.kt:73-80`) — there is no archive,
no export, no second slot, no history of rosters.

### New game — a three-stage wizard (`SetupScreen.kt`)

`SetupStage.SCRIPT → PLAYERS → BAG` (`SetupScreen.kt:58`, `:68`).

**Stage 1 — script** (`:136-260`). A card per script: name, author, character
count, "N homebrew", unknown-id warning (`:196-202`), and a four-token taste of
the script (`:204-218`). Two import buttons: paste (`:229-231` →
`ImportScriptDialog`, `:555-589`) and file (`:232-237` →
`rememberImportFileOpener`). Imported scripts get a delete button with a confirm
dialog (`:220-224`, `:243-259`). Built-in scripts are TB / BMR / SV only
(`GameData.kt:29-43`) — Experimental characters exist in the dataset (171
characters) but there is no built-in script that contains them.

**Stage 2 — players** (`:262-330`). `List(8) { "" }` seats (`:70`); one
`OutlinedTextField` per seat with a numeric prefix and an X to delete
(`:288-309`); "Add seat" appends one (`:310-321`); a one-line base distribution
label (`:281-286`, `:332-335`). No search, no reorder, no bulk entry, no roster
memory, **and no IME handling of any kind** — a repo-wide grep for
`ImeAction|KeyboardOptions|KeyboardActions|FocusRequester|LocalFocusManager`
returns **zero hits** in `app/src` and `web/src`.

**Stage 3 — bag** (`:337-502`). Header shows the adjusted distribution and the
bracket texts (`:365-384`), then Randomize / Clear (`:385-402`), a
"house rule: allow duplicates" checkbox (`:404-414`), validation issues
(`:425-439`), a name-only search field (`:440-448`), and a team-grouped list of
every town-resident character on the script with a checkbox (or a ± stepper for
`villageidiot`/`legion`/`riot`) plus a 2-line ability (`:449-480`, `:504-553`).
Three exits: "Deal randomly & start" (enabled only when the bag is exactly
`playerCount` and legal), "Deal anyway (I know what I'm doing)", and "Start empty
(assign in grimoire)" (`:481-500`).

`onStart` calls `viewModel.startGame(...)` then a second `update { deal(...) }`
(`:110-118`), and navigates to the game shell.

### The four setup prompts (`GameShell.kt:347-479`)

Once the game exists, `GameShell` pops up to four modal dialogs in a fixed
priority chain: Fortune Teller herring (`:347-376`), Drunk (`:377-413`), Lunatic
(`:415-440`), Marionette (`:442-479`), each guarded by `state.phase == SETUP`
and a local `rememberSaveable` "done" flag with a "Later" escape.
`HiddenIdentityDialog` (`:709-749`) is the shared picker.

"Begin night" re-validates the same four plus the bag
(`GameShell.kt:126-140` → `GameActions.validateSetupState`,
`GameActions.kt:503-561`) and offers "Fix setup" / "Start the night anyway"
(`GameShell.kt:551-591`). This guard is genuinely good work.

### Bluffs (`BluffsSheet.kt`)

One bottom sheet, one list of not-in-play Townsfolk/Outsiders (`:40-45`), tap to
toggle, hard cap of 3 (`:83-88`, `GameActions.kt:208-209`), and a "Suggest 3 for
me" chip (`:63-73` → `GameActions.suggestBluffs`, `GameActions.kt:121-127`).
Reachable from the overflow menu (`GameShell.kt:218-221`) and from a tiny
`+ bluffs` hot-zone at the top-left of the grimoire
(`GrimoireScreen.kt:176-197`). The chosen bluffs are inlined into the Demon-info
night step text (`NightOrder.kt:90`, `:103-108`) and are available as a
full-screen `BluffsCard` (`ShowCards.kt:388-393`).

There is exactly **one** bluff list in the whole model
(`GameState.demonBluffIds`, `GameState.kt:102`).

### Fabled (`GameExtras.kt:143-198`)

A bottom sheet listing all 17 Fabled with tap-to-toggle. Reachable only from
inside the game (overflow menu `GameShell.kt:238-241`, or the `fabled +`
hot-zone `GrimoireScreen.kt:198-219`) — i.e. **after** the bag has been built.

### Reveal (`RevealFlow.kt`)

Reached only from the overflow menu, "Reveal characters to players…"
(`GameShell.kt:230-233`). A full-screen black `Dialog`; for each seat with a
character: "Pass to \<name\> / Seat i of n / tap when only they can see", then
tap → "YOU ARE" + token + name + ability, then tap → next seat
(`RevealFlow.kt:61-131`). On completion it engages `PrivacyCover`
(`GameShell.kt:333-343`), which is a proper press-and-hold gate
(`PrivacyCover.kt:41-52`).

Name colour: `EmberRed` if evil, `AgedGold` otherwise, where "evil" is
`character.team.isEvil` for a player with a shown identity, else
`player.isEvil(...)` — which includes `alignmentFlipped` (`RevealFlow.kt:54-59`,
`GameState.kt:49-52`).

### End of game (`GameExtras.kt:234-350`)

`WinAdvisoryDialog` offers to declare a winner; `RevealSheet` lists every seat
with character, "shown as", cause of death, and a GOOD/EVIL WINS headline.
"End game & return home" calls `endGame()` — the state, the log and the
nomination history are deleted with no export.
`GameLogDialog` (`:46-106`) derives a chronological list from `deaths` and
`nominations` only.

### Reference (`ReferenceScreen.kt`, `LibraryScreen.kt`)

`ReferenceScreen` is used twice: as the in-game "Script" tab
(`GameShell.kt:312`) and inside `LibraryScreen` (`LibraryScreen.kt:56`). Three
sub-tabs (`:57-61`): **Characters** (search over name+ability, `:63-85`; grouped
rows with token + ability, `:92-141`), **Night order** (first and other night
lists filtered to the script, numbered, name-only, `:143-193`), **Jinxes**
(script-wide pairs, `:195-227`).

`LibraryScreen` puts one `PrimaryTabRow` tab per script (`:46-54`) and has no
import button of its own.

### Navigation, PWA, updates

Android: `NavHost` with HOME / SETUP / GAME / LIBRARY / NOTES_SETUP / NOTES
(`MainActivity.kt:73-151`), plus a splash gate on `viewModel.ready` (`:66-71`).
Web: a `rememberSaveable` `route: String` with the same six destinations
(`web/.../Main.kt:116-198`).

PWA shell: `index.html` registers `sw.js` and measures safe-area insets into
`window.__safeTop/__safeBottom` (`index.html:45-61`), which Compose then pads
with (`Main.kt:126-133`). `sw.js` is network-first for the shell and cache-first
with background refresh for everything else (`sw.js:9-52`), versioned by a
CI-stamped `__BUILD__`. `manifest.webmanifest` declares
`display:standalone`, `orientation:portrait`, and a single 512×512 icon.

`UpdateBanner` (Android only) polls a GitHub release and offers a one-tap APK
update (`UpdateBanner.kt:44-127`).

---

## Defects and gaps

### A. Starting a game

**1. P0 · "New game" silently destroys the game in progress.**
`HomeScreen.kt:189-191` navigates straight to the setup wizard with no warning
that `game != null`; `SetupScreen.kt:111` then calls
`viewModel.startGame(...)`, which clears the undo stack and overwrites the
single save slot (`GameViewModel.kt:83-87`, `WebGameViewModel.kt:67-71`;
`SavedData.game` is one nullable field, `data/SavedData.kt:11`). There is no
confirmation, no archive and no undo — the running game is gone.
*Repro:* start a game, tap ⋮ → "Back to home", tap "New game (storyteller)",
walk the wizard, tap "Deal randomly & start". The BMR game from five minutes ago
no longer exists.

**2. P0 · The Lunatic has no bluffs of its own, and no fake minions.**
`GameState` holds a single `demonBluffIds` (`GameState.kt:102`), capped at 3
(`GameActions.kt:208-209`). The wiki requires the Lunatic to be shown *its own*
three bluffs — which may include in-play characters — and a set of players it is
told are its Minions. Neither exists anywhere in state or UI. The Lunatic's own
first-night step (order index 16) is generated by the generic text-only branch
(`NightOrder.kt:142-178`) and prints correct prose — *"Show 3 character tokens of
arbitrary good characters"* — with no picker, no storage and no show card; the
only structured Lunatic handling is a sentence appended to the Demon's step
(`NightOrder.kt:110-115`, `:155-172`). This is the user's verbatim complaint
("Lunatic needs work: should have its own bluffs").
*Repro:* run BMR with a Lunatic; open ⋮ → Demon bluffs; there is one list, and
picking bluffs for the Lunatic corrupts the real Demon's bluffs.

**3. P0 · The Snitch cannot be run at all.**
Snitch requires **3 not-in-play bluffs per Minion**, shown *before* Demon info
(wiki `Snitch`; `night_guide.json` "snitch".first). The night order is already
correct (`snitch` is first-night index 15, between `MINION_INFO` at 14 and
`DEMON_INFO` at 18, `night_and_jinxes.json:310`) and the step's prose is
correct, but the step is built by the generic text-only branch
(`NightOrder.kt:142-178`): with one 3-slot global list
(`GameState.demonBluffIds`) there is nowhere to record two Minions' different
bluff sets, and the Show tool only offers `BluffsCard(state.demonBluffIds)`
(`ShowCards.kt:388-393`, `NightScreen.kt:783-786`). The storyteller must hold 6
character names in their head.
*Repro:* build a bag with a Snitch and two Minions; open the Snitch night step —
it tells you to show 3 tokens to each Minion and offers no way to choose or show
them.

**4. P1 · 16 of the 23 setup decisions are unprompted and unvalidated.**
Only Fortune Teller / Drunk / Lunatic / Marionette are prompted
(`GameShell.kt:347-479`) and validated (`GameActions.kt:503-561`). Evil Twin,
Bounty Hunter, Pixie, Village Idiot, Alchemist, Amnesiac, Xaan, Lord of Typhon,
Kazali, Lil' Monsta, Poppy Grower, traveller alignment, Fabled and the
Outsider-count choice all fall through to "the storyteller remembers".
The prompts are also hardcoded as four hand-written `if` blocks rather than a
table, so adding the 17th costs another 35 lines of `GameShell`.

**5. P1 · The prompts are hardcoded to `phase == SETUP`, so mid-game identity
changes get nothing.** `GameShell.kt:350`, `:380`, `:418`, `:445` all gate on
`state.phase == Phase.SETUP`. A Pit-Hag creating a Drunk on night 3, a Kazali
making Minions on night 1, or an Amnesiac being decided late produce no prompt
and no validation.

**6. P1 · Entering 12 names is a 36-gesture chore.**
`PlayersStage` (`SetupScreen.kt:288-309`) gives each seat a plain
`OutlinedTextField` with the platform-default IME action. There is **no**
`ImeAction.Next`, no `FocusRequester` chain, no bulk paste, no roster memory
(nothing in `SavedData` stores past rosters, `data/SavedData.kt:9-15`), and no
reorder. For 12 players starting from 8 seats: 4 taps to add seats, then for each
of 12 seats tap-field → type → dismiss-keyboard → tap-next-field.
*Repro:* new game → step 2 → type "Uri" → the keyboard's Done key closes the
keyboard instead of moving to seat 2.

**7. P1 · On the iPhone PWA the software keyboard covers the lower name
fields.** `index.html:49-60` measures only `env(safe-area-inset-*)`; it never
listens to `window.visualViewport`. On iOS the keyboard overlays the layout
viewport rather than resizing it, and the Compose canvas is `position:fixed;
inset:0; height:100vh` (`index.html:20-33`), so seats ~7-12 sit underneath the
keyboard. `safeDrawingPadding()` (`SetupScreen.kt:80`) resolves to the JS-fed
insets on web, which do not include the keyboard.
*Repro (needs a device):* open the PWA on iPhone, new game, tap seat 10's field.

**8. P1 · The bag builder cannot express a Kazali, a Lil' Monsta, a Summoner
seat count, or a traveller.**
- Kazali `[You choose which players are which Minions. -? to +? Outsiders]`:
  `Setup.modifierFor` relaxes only OUTSIDER (+TOWNSFOLK), so
  `validateBag` still demands the normal Minion count
  (`GameActions.kt:456-478`) and reports "Minion: 0 in bag, expected 2".
- Lil' Monsta `[+1 Minion]` (`characters.json`): the bracket says nothing about
  the Demon, so the builder insists a `lilmonsta` token be dealt to a *seat*,
  which the rules forbid ("Lil' Monsta is a token, not a player").
- Travellers: `BagStage(playerCount = names.size)` (`SetupScreen.kt:106`) — there
  is no way to mark a seat as a Traveller during setup, and travellers must not
  count toward the distribution. `GameActions.deal` counts non-travellers
  (`GameActions.kt:314`), so a 12-seat table with one traveller cannot be dealt
  from the wizard at all.
The only escape is "Deal anyway (I know what I'm doing)" (`SetupScreen.kt:492`),
which turns off *all* checking.

**9. P1 · The "Need:" line contradicts the validator for every choice bracket.**
`Setup.modifierFor` comments *"Ranged choice ('-1 or +1'): apply the last listed
option as the suggested default"* and does exactly that
(`Setup.kt:203-208`). So with a Godfather, the header
(`SetupScreen.kt:372-378`) says "Need: 6 townsfolk · 3 outsiders …" — the +1
branch only — while `validateBag` happily accepts the −1 branch
(`Setup.allowedDistributions`, `Setup.kt:261-272`). A storyteller who trusts the
header builds the wrong bag; one who trusts the checker distrusts the header.
Team-warping brackets are worse: with an Atheist or a Legion the header still
prints "1 minion · 1 demon" because those modifiers carry zero deltas
(`Setup.kt:127-129`).
*Repro:* new game → any script with the Godfather → step 3 → compare the header
against what the checker accepts.

**10. P1 · No Fabled selection before the bag is built.**
`FabledSheet` lives only inside the running game (`GameShell.kt:238-241`).
`BagStage` calls `GameActions.validateBag(selected, playerCount, allowAnyDuplicates = …)`
(`SetupScreen.kt:356`) — note the missing `fabledIds` argument — so the Sentinel's
±1 Outsider is invisible to the wizard even though `validateBag` implements it
(`GameActions.kt:444-455`) and `validateSetupState` passes it
(`GameActions.kt:511`). The wizard blocks a legal Sentinel bag and the "Begin
night" guard then allows it. Two validators, two answers.

**11. P1 · Randomize is all-or-nothing.**
`SetupScreen.kt:387-397` replaces the entire bag. There is no way to pin
characters in ("I want the Drunk tonight"), ban characters out ("no Mastermind
this time"), or re-roll only the Townsfolk. `GameActions.randomBag`
(`GameActions.kt:338-402`) has no `pinned`/`banned` parameters and does not
receive the `allowDuplicates` house rule the UI is showing.

**12. P1 · The chosen bag is invisible.**
"7 of 12 chosen" (`SetupScreen.kt:379-383`) is the only feedback; which seven is
scattered across a ~25-row scrolling list as checkbox states. Per-team progress
("3/5 townsfolk") appears only indirectly, and only once the counts are already
wrong (`:425-439`).

**13. P2 · Cancelling setup destroys the roster with no confirmation.**
`ScriptStage`'s "Cancel" (`SetupScreen.kt:169`) calls `onBack` →
`nav.popBackStack()` (`MainActivity.kt:96`), which drops the whole `SetupScreen`
composition including 12 typed names and a hand-built bag.

**14. P2 · The remove-seat X sits flush against the name field.**
`SetupScreen.kt:302-307`: an `IconButton` immediately right of the text field,
no confirmation, no undo. A mis-tap while reaching for the keyboard deletes a
typed name.

**15. P2 · No seat reordering during setup; in-game reordering is one step at
a time.** `ReorderSeatsDialog` (`GameExtras.kt:109-140`) offers up/down
`IconButton`s calling `GameActions.moveSeat(±1)` — moving seat 12 to position 2
is ten taps — and it is only reachable from the in-game overflow menu
(`GameShell.kt:250-253`). Nothing reorders during the wizard.

**16. P2 · No Experimental built-in script.** `GameData.builtInScripts()` hard
codes tb/bmr/sv (`GameData.kt:29-33`). The 171-character dataset includes every
Experimental character, but reaching one requires importing a script.

**17. P3 · Bag search matches names only** (`SetupScreen.kt:450`) whereas the
reference screen matches names *and* abilities (`ReferenceScreen.kt:78-81`).

### B. Revealing tokens to players

**18. P0 · A flipped Ogre (or any alignment override) is shown red.**
`RevealFlow.kt:54-59` computes `evil` from `player.isEvil(...)`, which XORs in
`alignmentFlipped` (`GameState.kt:49-52`), and paints the character name
`EmberRed` (`:114`). The wiki is explicit that the Ogre *"is not told whether
they are evil"*. The same leak fires for the Bounty Hunter's evil Townsfolk and
for a Mezepheles/Pit-Hag conversion.
*Repro:* seat sheet → Flip alignment on the Ogre → ⋮ → "Reveal characters to
players…" → that player's own card is red.

**19. P1 · The reveal is a hidden menu item, not part of starting a game.**
After "Deal randomly & start" (`SetupScreen.kt:110-118`) the app lands on the
grimoire with all 12 characters on screen. Handing out identities — the single
most important physical step — is buried at position 4 of a 13-item overflow menu
(`GameShell.kt:230-233`). Worse, the four identity prompts fire *after* the deal
(`GameShell.kt:347-479`), so a storyteller who reveals first shows the Drunk a
card that says **"YOU ARE Drunk"**.

**20. P1 · One stray tap burns a player's reveal.**
The entire screen is a single `clickable` (`RevealFlow.kt:69-76`): tap 1 shows
the card, tap 2 advances irreversibly. There is no press-and-hold gate — even
though the codebase already has exactly that pattern in `PrivacyCover.kt:41-52`
— no back step, no "show me that again". Handing a phone across a table with a
live tap target on it is how identities leak.

**21. P1 · No per-seat re-reveal and no progress memory.**
`index` is `rememberSaveable` *inside* `RevealFlow`, which is mounted under
`if (showRevealFlow)` (`GameShell.kt:333`), so closing and reopening restarts at
seat 1. Late arrival, a Pit-Hag change, a Huntsman turning the Damsel into a
Townsfolk, a Professor resurrection that needs first-night info re-run — all
require tapping through everybody.

**22. P1 · Travellers are never told their alignment.**
`RevealFlow.kt:45` includes any seat with a `characterId`, so a traveller gets a
"YOU ARE \<Traveller\>" card coloured gold (`Team.TRAVELLER.isEvil == false`,
`Character.kt:16`) regardless of the storyteller's alignment choice. The
alignment must be delivered separately via ⋮ → "Show a card…" → Good/Evil
(`ShowCards.kt:386-387`), which is not mentioned anywhere in the flow.

**23. P1 · No Evil Twin pairing step.** The Evil Twin's twin is a setup-time
storyteller choice with a hard constraint (opposite alignment) and a persistent
TWIN reminder (`characters.json` `eviltwin.reminders = ["Twin"]`). Nothing in
`SetupScreen`, `GameShell`'s prompt chain, or `validateSetupState` mentions it;
the storyteller must place the reminder by hand from the night tray and run the
mutual reveal from `night_guide.json` prose.

**24. P2 · The reveal shows nothing the *other* party needs.** The Lunatic's card
is right, but the paired obligations — show the real Demon "This player is the
Lunatic", show the Demon the Marionette, wake the twins together — are night-sheet
text only. The reveal flow should be able to run these paired hand-overs, because
it is the only screen that already knows how to hand the phone to a named seat.

**25. P2 · Seat order reveal is a privacy tell.** `RevealFlow.kt:45` walks seats
1..n. Everyone at the table can see who is being handed the phone and roughly how
long each person looks. A shuffled order (fixed per game) removes the tell at no
cost.

### C. Reference material at the table

**26. P1 · The in-game Script tab does not mark what is in play.**
`GameShell.kt:312` passes only `state.script` to `ReferenceScreen`, so
`CharacterSheet` (`ReferenceScreen.kt:92-141`) cannot mark in-play characters —
even though `CharacterPicker` already implements exactly that badge
(`SeatSheet.kt:469-476`). "Is the Empath in play?" is the question a storyteller
asks most, and the Script tab cannot answer it.

**27. P1 · The night-order tab is a list of names with no instructions.**
`NightOrderRow` (`ReferenceScreen.kt:172-193`) renders `position. Name` and
nothing else. `characters.json` carries `firstNightReminder` /
`otherNightReminder` — the exact text printed on the physical night sheet — and
they are never displayed here. Rows are not tappable, so there is no path from
the night order to a character's ability, reminders or jinxes.

**28. P1 · Travellers and Fabled are missing from the reference for built-in
scripts.** `GameData.builtIn` filters `it.team.isTownResident`
(`GameData.kt:39-42`), so `resolve(script)` never yields a Traveller or Fabled,
and `CharacterSheet`'s TRAVELLER/FABLED sections (`ReferenceScreen.kt:110`) are
always empty for TB/BMR/SV. When a traveller joins mid-game the only place to
read their ability is the seat's character picker.

**29. P2 · The Library's script tabs do not scroll.**
`LibraryScreen.kt:46` uses `PrimaryTabRow` (fixed-width), not
`PrimaryScrollableTabRow`. Three built-ins plus a handful of imports squeezes
every tab label to unreadable width on a phone. The Library also has no import
button — scripts can only be added from New Game or Notes setup.

**30. P2 · Two different jinx views disagree.** `ReferenceScreen`'s Jinxes tab
lists every jinx on the *script* (`:53-54`); `ActiveJinxesDialog`
(`GameExtras.kt:201-232`) lists only jinxes between *assigned* characters. Same
data, two entry points, no cross-reference; and the useful one (in-play) is in
the overflow menu, not on the Script tab.

**31. P2 · Character rows are not tappable anywhere in the reference.** No
detail view: no reminder tokens, no night position, no per-character jinxes, no
"how to run" prose — even though `NightGuide` already holds 116 entries of
exactly that prose (`NightGuide.kt`, `night_guide.json`).

### D. Script import

**32. P1 · Two scripts with the same name silently overwrite each other.**
`ScriptParser.parse` derives `id = "imported-" + normalizeId(name)`
(`Script.kt:74-82`) and the store does
`importedScripts.filterNot { it.id == script.id } + script`
(`GameViewModel.kt:240-243`). Any script JSON without a `_meta` block falls back
to `name = "Imported script"` → id `imported-importedscript`, so **every unnamed
import replaces the previous one** with no warning.
*Repro:* import two different raw `["washerwoman", …]` arrays; only the second
survives.

**33. P1 · The most common iPhone share form — a URL to a script page — is
rejected.** `ScriptLink.isLink` returns true for any `https://` string
(`ScriptLink.kt:13-16`) but `decode` returns null unless the text contains
`script=` (`:21-24`), producing "Couldn't import: that looks like a link, but it
has no readable ?script=… payload" (`GameViewModel.kt:230-231`). Links to
botc-scripts, a Gist, or a raw `.json` URL cannot be imported at all — nothing in
the app fetches a URL. On iOS this is the normal way a script arrives.

**34. P1 · The PWA ignores its own URL.** `Main.kt:51-73` never reads
`window.location.search`. A `?script=…` query — the exact payload the official
script tool produces, and something `ScriptLink.decode` already handles — could
import on open. `manifest.webmanifest` also declares no `share_target`, so the
iOS share sheet cannot send a script to the app.

**35. P2 · Importing from a file gives no success feedback.**
`ScriptStage`'s file callback sets `fileError = viewModel.importScript(text)`
(`SetupScreen.kt:147-149`), which is `null` on success — so a successful import
shows nothing at all. The new script is appended to the *end* of the list
(`GameViewModel.kt:240-243`), below the three built-ins, so on a phone it is
often off-screen.

**36. P2 · The paste dialog is the wrong container for a phone.** A 200 dp,
6-line `OutlinedTextField` inside an `AlertDialog`
(`SetupScreen.kt:572-578`) leaves almost no room once the keyboard is up, there
is no Paste button, and there is no live preview of what was parsed (name,
author, counts, unknown ids) before committing.

**37. P2 · Duplicate ids are dropped silently.** `characterIds = ids.distinct()`
(`Script.kt:80`) — legitimate for keyed lists, but a hand-edited script with a
duplicated Village Idiot loses a copy with no message, while `unknownIds` *does*
get reported (`SetupScreen.kt:196-202`).

### E. Session lifecycle, navigation, platform

**38. P1 · Ending a game destroys the log.** `RevealSheet`'s "End game & return
home" (`GameExtras.kt:344`) → `endGame()` → the whole `GameState` is nulled
(`GameViewModel.kt:89-98`). Deaths, nominations, votes, bluffs and storyteller
notes are unrecoverable. There is no export, no share, no "last game" archive —
and the reveal sheet itself is the only place that record was ever visible.

**39. P1 · The PWA can lose the game to browser storage eviction.**
`WebStore` writes one localStorage key (`WebApp.kt:19`, `:29-35`) and nothing
calls `navigator.storage.persist()`. On iOS, a non-installed PWA's storage is
capped at 7 days of non-use; on any browser, storage pressure can evict it. The
`catch` on save (`WebApp.kt:32-34`) swallows quota errors — the storyteller
learns their game is gone when they next open the app.

**40. P1 · The PWA never tells you which build you are running or that a new
one exists.** `Main.kt:180-198` calls `HomeScreen(...)` without `buildLabel`, so
the footer is hidden (`HomeScreen.kt:229`). `UpdateBanner` is Android-only
(`MainActivity.kt:40`). `sw.js` is network-first for the shell so a new build
lands on the *next* launch (`sw.js:33-40`), but nothing prompts a reload and
nothing displays the version. The user plays on the iPhone PWA — this is exactly
why "did my fix ship?" is unanswerable.

**41. P1 · The screen sleeps mid-game on iOS.**
`KeepScreenOn` requests the wake lock once, on first composition, and never
re-acquires it (`web/.../WebUiPlatform.kt:12-19`). Browsers release the screen
wake lock whenever the page is hidden; after any app switch or auto-lock the
grimoire goes back to sleeping. It is also only invoked from `GameShell`
(`GameShell.kt:110`) — the setup wizard and the reveal flow can sleep while the
phone is being passed around the table.

**42. P2 · The PWA is locked to portrait.**
`manifest.webmanifest:8` sets `"orientation": "portrait"`. A 15-seat circle and
the night sheet both read better in landscape, and the storyteller often props
the phone on the table.

**43. P2 · Manifest gaps.** One 512×512 icon declared twice (`:11-14`) with no
192×192, no `id`, no `display_override`, no `shortcuts` (a "Resume game" /
"New game" shortcut is free), no `share_target` (see #34).

**44. P2 · The service worker deletes the whole cache on activate.**
`sw.js:20-26` drops every non-current cache and claims clients immediately after
`skipWaiting()` (`:18`). A running game whose shell was just updated will re-fetch
icons and `data/*.json` on demand; if the update lands and the network then
drops, the running session loses its offline safety net mid-game.

**45. P3 · The Home resume card omits the timestamp.** `GameState.updatedAt`
exists (`GameState.kt:114`) but `HomeScreen.kt:174-184` never shows it. "Resume
game · Bad Moon Rising · 12 players · night 3" does not tell you whether that was
tonight or three weeks ago.

**46. P3 · `PrivacyCover.kt:54` renders `Text("", fontSize = 64.sp)`** — an empty
placeholder where a glyph was clearly intended, leaving 64 dp of dead space.

---

## Proposed behaviour (spec)

### S1. One "Game setup" screen, not a three-step wizard

Replace `SetupStage` (`SetupScreen.kt:58`) with a single scrolling screen of
four collapsible cards over a persistent action bar. Every card shows a
one-line summary when collapsed, so the whole state of the game-to-be is legible
at a glance and any card can be revisited without losing the others.

```
┌──────────────────────────────────────────────┐
│ ← Cancel        New game            [Reset]  │
├──────────────────────────────────────────────┤
│ ▸ 1 SCRIPT      Bad Moon Rising · 25 chars ✓ │
├──────────────────────────────────────────────┤
│ ▾ 2 TABLE                        12 seats ✓  │
│   Base: 7 TF / 2 OUT / 2 MIN / 1 DEMON       │
│   ┌────────────────────────────────────────┐ │
│   │ ⠿ 1  Uri                          ✕    │ │
│   │ ⠿ 2  Dana                         ✕    │ │
│   │ ⠿ 3  ▸ (typing…)                       │ │
│   │  …                                      │ │
│   └────────────────────────────────────────┘ │
│   [+ seat] [Paste list] [⟲ Last game (12)]   │
│   [Traveller ▾]  seats marked ⛨ don't count  │
├──────────────────────────────────────────────┤
│ ▾ 3 BAG                            9 / 12    │
│   ┌── in the bag ───────────────────────┐    │
│   │ ⬤Chef ⬤Empath ⬤Monk 📌⬤Drunk ⬤Baron │    │  ← sticky tray, tap to remove
│   │ ⬤Imp …                    (scroll →)│    │
│   └─────────────────────────────────────┘    │
│   TF ▰▰▰▰▱ 4/5   OUT ▰▱ 1/2                  │
│   MIN ▰▰ 2/2     DEM ▰ 1/1                   │
│   ⚠ Godfather: play 1 OR 3 outsiders  [1][3] │
│   [🎲 Randomize] [🎲 Fill the rest] [Clear]  │
│   [search…]                                  │
│   ── TOWNSFOLK ─────────────────────────     │
│   ☐ 📌 Chambermaid   Each night, choose…     │
├──────────────────────────────────────────────┤
│ ▸ 4 FABLED & HOUSE RULES        Sentinel ✓   │
├──────────────────────────────────────────────┤
│  ▶ DEAL & HAND OUT TOKENS        (12 ready)  │
│  Start empty · assign by hand                │
└──────────────────────────────────────────────┘
```

Rules:
- The action bar is always visible and always states what will happen next.
- `📌` pins a character into every randomize; long-press bans it.
- The bag tray is horizontally scrollable and is the *only* place a storyteller
  needs to look to know the bag.
- A `⛨` traveller seat is excluded from `playerCount` for every distribution and
  validation call, and is dealt no token.

Fixes #1 (below), #10, #11, #12, #13, #15, #16.

### S2. Fast, forgiving name entry

- `KeyboardOptions(imeAction = ImeAction.Next)` on every seat field, `Done` on
  the last; a `FocusRequester` list advances focus in `KeyboardActions(onNext=…)`.
  Typing 12 names becomes type-Next-type-Next.
- **Paste list**: a single multi-line field that splits on newline / comma /
  semicolon and fills seats, growing the seat count to match. Round-trips: a
  "Copy list" action on the resume card.
- **Roster memory**: add to `SavedData`
  ```kotlin
  @Serializable data class Roster(val names: List<String>, val usedAt: Long)
  data class SavedData(…, val recentRosters: List<Roster> = emptyList())  // keep 5
  ```
  written on `startGame` and offered as `⟲ Last game (12)` chips. Rosters are
  also the source for name **autocomplete** on each field.
- **Drag to reorder** (`⠿` handle) in the setup list *and* in
  `ReorderSeatsDialog`; keep the ±1 buttons as an accessibility fallback.
- The `✕` becomes a swipe-to-delete with a 5-second "Undo" snackbar.
- `SetupScreen` calls `KeepScreenOn()`.

Fixes #6, #14, #15, #41 (partly).

### S3. iOS keyboard inset (PWA)

In `index.html`, alongside `computeSafeInsets`:

```js
function computeKeyboardInset() {
  const vv = window.visualViewport;
  window.__keyboardInset = vv
    ? Math.max(0, window.innerHeight - vv.height - vv.offsetTop) : 0;
}
if (window.visualViewport) {
  visualViewport.addEventListener('resize', computeKeyboardInset);
  visualViewport.addEventListener('scroll', computeKeyboardInset);
}
computeKeyboardInset();
```

`Main.kt` reads `jsKeyboardInset()` into the root `Box` padding next to
`jsSafeBottom()`, and recomposes on change (poll on an animation frame or push
via a `MutableStateFlow` set from a JS callback). Fixes #7.

### S4. A data-driven "Before night 1" checklist

Replace the four hand-written dialogs (`GameShell.kt:347-479`) with a declarative
table in the engine, and render it as a checklist card on the grimoire during
SETUP (and as a re-openable sheet at any time).

```kotlin
// engine/SetupTasks.kt
enum class TaskKind { PICK_SHOWN_CHARACTER, PICK_PLAYER, PICK_PLAYERS,
                      PICK_BLUFF_SET, PICK_NUMBER, PICK_CHARACTER, FLIP_ALIGNMENT, ACK }

data class SetupTask(
  val id: String,                       // "fortuneteller.herring"
  val triggerId: String,                // character id that raises it
  val kind: TaskKind,
  val title: String,                    // "Fortune Teller red herring"
  val prompt: String,                   // storyteller voice, imperative
  val required: Boolean,                // blocks "Begin night"
  val candidates: (GameState, Lookup) -> List<Candidate>,
  val apply: (GameState, Selection) -> GameState,
  val satisfied: (GameState, Lookup) -> Boolean,
)

object SetupTasks { fun activeFor(state, lookup): List<SetupTask> }
```

Seed table (each row = one entry from the §1 rules table; `required` = blocks
"Begin night"):

| id | kind | candidates | applies | required |
|---|---|---|---|---|
| `fortuneteller.herring` | PICK_PLAYER | good players | `PlacedReminder("fortuneteller","Red herring")`, exclusive | ✔ |
| `drunk.shown` | PICK_SHOWN_CHARACTER | not-in-play Townsfolk | `setShownCharacter` + `Is the Drunk` + note | ✔ |
| `lunatic.shown` | PICK_SHOWN_CHARACTER | script Demons | `setShownCharacter` + note | ✔ |
| `lunatic.minions` | PICK_PLAYERS(n = minion count) | any player ≠ Lunatic | `PlacedReminder("lunatic","Fake minion")` ×n | ✔ |
| `lunatic.bluffs` | PICK_BLUFF_SET(3, allowInPlay = true) | any good character on script | `bluffSets["lunatic"]` | ✔ |
| `marionette.shown` | PICK_SHOWN_CHARACTER | not-in-play good, town-resident | `setShownCharacter` + `Is The Marionette` | ✔ |
| `marionette.neighbour` | ACK | — | asserts a Demon neighbour | ✔ (already in `validateSetupState`) |
| `eviltwin.twin` | PICK_PLAYER | players of **opposite** alignment to the Evil Twin | `PlacedReminder("eviltwin","Twin")`, exclusive | ✔ |
| `snitch.bluffs.<minionSeatId>` | PICK_BLUFF_SET(3) | not-in-play characters | `bluffSets["minion:<id>"]` — one row **per Minion** | ✔ |
| `bountyhunter.evilTownsfolk` | FLIP_ALIGNMENT | in-play Townsfolk | `flipAlignment` + note | ✔ |
| `bountyhunter.known` | PICK_PLAYER | evil players | `PlacedReminder("bountyhunter","Know")`, exclusive | ✔ |
| `pixie.madAs` | PICK_CHARACTER | **in-play** Townsfolk | `PlacedReminder("pixie","Mad")` + record which | ✔ |
| `villageidiot.drunk` | PICK_PLAYER | seats holding a Village Idiot | `PlacedReminder("villageidiot","Drunk")`, exclusive | ✔ (only when ≥2 copies) |
| `alchemist.ability` | PICK_CHARACTER | not-in-play Minions | `Is The Alchemist` + note | ✔ |
| `amnesiac.ability` | free text | — | storyteller note on the seat | ✔ |
| `xaan.x` | PICK_NUMBER | 0…outsider count | `PlacedReminder("xaan","X")` + value | ✔ |
| `lordoftyphon.minions` | PICK_PLAYERS(2) + PICK_CHARACTER each | the Demon's two neighbours | `assignCharacter` on each | ✔ |
| `demon.bluffs` | PICK_BLUFF_SET(3) | not-in-play good | `bluffSets["demon"]` | ✔ |
| `summoner.bluffs` | PICK_BLUFF_SET(3) | not-in-play good | `bluffSets["summoner"]` | ✔ |
| `traveller.alignment.<seatId>` | ACK(good/evil) | — | `flipAlignment` as needed | ✔ per traveller |
| `kazali.noMinions` | ACK | — | marks the bag legal with 0 Minions | ✔ |
| `lilmonsta.noDemonSeat` | ACK | — | marks the bag legal with 0 Demon seats, +1 Minion | ✔ |
| `poppygrower.skipInfo` | ACK | — | suppresses MINION_INFO / DEMON_INFO steps | — |
| `setup.outsiderChoice` | PICK_NUMBER | the bracket's legal counts | records which branch is being played | ✔ when a choice bracket is in the bag |

`validateSetupState` (`GameActions.kt:503-561`) becomes
`SetupTasks.activeFor(state).filter { it.required && !it.satisfied(state) }` —
one implementation instead of a growing `when` block, and every future character
is a table row.

UI:

```
┌──────────────────────────────────────────────┐
│ BEFORE THE FIRST NIGHT              3 / 9 ✓  │
├──────────────────────────────────────────────┤
│ ✓ Demon bluffs            Saint, Fool, Tinker│
│ ✓ Drunk sees              Chambermaid        │
│ ✓ Fortune Teller herring  Dana (seat 4)      │
│ ○ Lunatic sees            ▸ choose a Demon   │
│ ○ Lunatic's "minions"     ▸ pick 2 players   │
│ ○ Lunatic's bluffs        ▸ pick 3           │
│ ○ Snitch → Ari's bluffs   ▸ pick 3           │
│ ○ Snitch → Sam's bluffs   ▸ pick 3           │
│ ○ Evil Twin's twin        ▸ pick a good pl.  │
├──────────────────────────────────────────────┤
│      [ Hand out tokens ]   [ Begin night ]   │
└──────────────────────────────────────────────┘
```

`Begin night` stays enabled-with-a-guard (the existing "Start the night anyway"
escape at `GameShell.kt:584-589` is correct and must survive).

Fixes #4, #5, #8 (the ACK rows), #23.

### S5. Bluff **sets**, not one list

Model change:

```kotlin
// GameState
@Deprecated("migrate to bluffSets") val demonBluffIds: List<String> = emptyList(),
/** key -> up to 3 character ids. Keys: "demon", "lunatic", "summoner",
 *  "minion:<playerId>" (Snitch). */
val bluffSets: Map<String, List<String>> = emptyMap(),
```

Migration: on load, if `bluffSets` is empty and `demonBluffIds` is not,
`bluffSets = mapOf("demon" to demonBluffIds)`.

`BluffsSheet` becomes a tabbed sheet whose tabs are derived from what is in
play — never shown when the game needs only one set:

```
┌──────────────────────────────────────────────┐
│ Bluffs                                       │
│ [ Demon ]  [ Lunatic ]  [ Ari ✱ ] [ Sam ✱ ]  │  ✱ = Snitch minion
├──────────────────────────────────────────────┤
│ Lunatic bluffs — 1/3        [Suggest 3]      │
│ Shown to Uri, who thinks they are the Zombuul│
│ ⚠ May include in-play characters (wiki)      │
│ [search…]                                    │
│ ⬤ Chambermaid  IN PLAY   Each night, choose… │
│ ⬤ Courtier  •                                │
│ ⬤ Exorcist     ⚠ shown to the Drunk          │
└──────────────────────────────────────────────┘
```

Per-set rules:
- `demon`, `summoner`, `minion:*` → candidates = not-in-play good (Townsfolk +
  Outsider); flag any candidate that is a Drunk/Marionette *shown* character
  with `⚠ shown to the Drunk` (today's filter uses `characterId` only,
  `BluffsSheet.kt:40-45`, so those slip through unlabelled).
- `lunatic` → candidates = **all** good characters on the script, in-play ones
  badged `IN PLAY`, per the wiki.
- A search field (the list is ~20 rows on TB and 40+ on homebrew).
- `Suggest 3` per tab, seeded so two sets do not collide by accident.
- `GameActions.setBluffs(key, ids)` replaces the global setter; the Demon-info
  night step reads `bluffSets["demon"]`, and a new Snitch step reads each
  `minion:<id>` and orders itself **before** DEMON_INFO (per the wiki).

Fixes #2, #3, and the `⚠ shown to the Drunk` half of the bluff-quality problem.

### S6. Hand-out mode (replaces `RevealFlow`)

A first-class destination, offered automatically after the deal and reachable
from the checklist ("Hand out tokens") and the overflow menu.

```
┌──────────────────────────────────────────────┐
│ HAND OUT TOKENS                    5 / 12    │
│ Pass the phone; each player holds to reveal. │
├──────────────────────────────────────────────┤
│  ✓ Uri      ✓ Dana     ✓ Ari    ✓ Sam        │
│  ✓ Mia      ▶ Jon      ○ Lea    ○ Tom        │
│  ○ Ben      ○ Ivy      ○ Max    ○ Zoe        │
│  (tap any name to (re)show that seat only)   │
├──────────────────────────────────────────────┤
│  Paired hand-overs still to run:             │
│   • Demon ← "This player is the Lunatic"     │
│   • Evil Twin ⇄ Dana (both together)         │
├──────────────────────────────────────────────┤
│              [ Next: Jon ]                   │
└──────────────────────────────────────────────┘
```

The per-seat screen:

```
┌──────────────────────────────────────────────┐
│                                              │
│                 Pass to                      │
│                  JON                         │
│                                              │
│          ╭──────────────────────╮            │
│          │  HOLD  to reveal     │            │  ← 700 ms press-and-hold,
│          ╰──────────────────────╯            │     same gate as PrivacyCover
│                                              │
│  seat 6 of 12          [I'll do this later]  │
└──────────────────────────────────────────────┘
        ↓ (finger held)
┌──────────────────────────────────────────────┐
│                  YOU ARE                     │
│                   (token)                    │
│                 CHAMBERMAID                  │  ← colour = the character's own
│   Each night, choose 2 alive players (not    │     team, never the ST's override
│   yourself): you learn how many woke tonight │
│                                              │
│           release to hide                    │
└──────────────────────────────────────────────┘
```

Behaviour:
- **Hold, not tap.** Reuse `PrivacyCover`'s `detectTapGestures(onPress = …)` +
  `withTimeoutOrNull` pattern (`PrivacyCover.kt:41-52`). Releasing hides
  immediately; a stray tap in transit shows nothing. Fixes #20.
- **Colour rule (fixes #18):** the name colour is
  `characterShownToPlayerId → character.team` — the *believed* character's own
  team. `alignmentFlipped` never affects it. A separate, explicit
  `alignmentToTell: Alignment?` per seat drives an extra "YOU ARE EVIL / GOOD"
  page, defaulting to: travellers → ask the storyteller (fixes #22); Ogre →
  **never**; everyone else → none.
- **Progress is state, not local composition:** `Player.tokenShownAt: Long?`.
  Re-opening resumes; tapping a name re-shows one seat; a character change
  (Pit-Hag, Huntsman→Damsel, star-pass) clears the flag and the checklist
  re-raises "Jon needs a new token". Fixes #21.
- **Shuffled order** fixed per game (`Random(seed = game.createdAt)`), with a
  "seat order" toggle. Fixes #25.
- **Paired hand-overs** run from the same screen: Evil Twin (both twins, two
  pages), Demon ← Marionette, Demon ← Lunatic, Lil' Monsta babysitter, Kazali's
  new Minions. Each is a checklist row that opens hand-out mode scoped to those
  seats. Fixes #24.
- On finish it still engages `PrivacyCover` (that part of
  `GameShell.kt:336-341` is right).

### S7. Reference that works at the table

`ReferenceScreen(viewModel, script, state: GameState? = null)`:

```
┌──────────────────────────────────────────────┐
│ [ Characters ] [ Night order ] [ Jinxes 3 ]  │
│ [search names & abilities…]  [◉ in play only]│
├──────────────────────────────────────────────┤
│ TOWNSFOLK (13)                               │
│ ⬤ Chambermaid  ● IN PLAY (seat 3, Ari)       │
│      Each night, choose 2 alive players…     │
│ ⬤ Courtier                                   │
├──────────────────────────────────────────────┤
```
tapping a row →
```
┌──────────────────────────────────────────────┐
│ ⬤ CHAMBERMAID          Townsfolk · BMR       │
│ Each night, choose 2 alive players (not      │
│ yourself): you learn how many woke tonight   │
│ due to their ability.                        │
│ ── in play ── seat 3 (Ari), alive            │
│ ── night ──  1st: #12   other: #22           │
│   "Point to Chambermaid, then two players…"  │  ← firstNightReminder
│ ── reminders ── [Chambermaid]                │
│ ── jinxes ──  none on this script            │
│ ── how to run ──  (night_guide prose)        │
└──────────────────────────────────────────────┘
```

Night-order tab rows carry the sheet text and the in-play seat:

```
 12. Chambermaid   Ari         "Point to Chambermaid, then two…"
 13. Exorcist      —  not in play
```

Also: `builtIn()` keeps a `characterIds` list of town residents but the
reference resolves Travellers and Fabled for the script's editions
(`GameData.travellersFor` + `allFabled`) into their own sections (fixes #28);
`LibraryScreen` uses `PrimaryScrollableTabRow` and gains an "Import script"
action (fixes #29); the Jinxes tab defaults to "in play" when `state != null`
and offers "all on this script" as a toggle, retiring `ActiveJinxesDialog`
(fixes #30, #31, #26, #27).

### S8. Script import that survives an iPhone

1. **Stable ids.** `Script.id = "imported-" + sha1(normalizedCharacterIds + name).take(10)`.
   On import, if an existing script has the same id → "Already imported, updated";
   same *name* but different id → "You already have a script called X" with
   `[Replace] [Keep both]`. Fixes #32, #37 (report dropped duplicate ids too).
2. **Fetch URLs.** If the pasted text is a URL without `script=`, fetch it
   (`window.fetch` on web, `HttpURLConnection` on Android — the pattern already
   exists in `UpdateBanner.kt:65-70`) and parse the body. Handle
   `https://botc-scripts.…/script/<n>/…` and any raw `.json`. On CORS failure,
   say so and offer "Open in browser → download → Import from file". Fixes #33.
3. **URL and share entry points.** In `Main.kt`, before `ComposeViewport`:
   read `window.location.search`; if it contains `script=`, run
   `ScriptLink.decode` + `ScriptParser.parse` and land on the setup screen with
   that script preselected. Add to `manifest.webmanifest`:
   ```json
   "share_target": { "action": "./", "method": "GET",
                     "params": { "text": "script", "url": "script" } }
   ```
   so the iOS/Android share sheet can send a script link straight into the PWA.
   Fixes #34.
4. **A real import screen, not an AlertDialog:** full-height sheet, a Paste
   button, and a parsed preview before committing:
   ```
   ┌────────────────────────────────────────┐
   │ Import script                          │
   │ [ Paste ] [ From file ] [ From link ]  │
   │ ┌────────────────────────────────────┐ │
   │ │ https://script.bloodontheclockt…   │ │
   │ └────────────────────────────────────┘ │
   │ ── preview ──────────────────────────  │
   │  No Greater Joy   by Ben Burns         │
   │  13 TF · 4 OUT · 4 MIN · 4 DEM         │
   │  ⚠ 1 unknown id skipped: "custom_x"    │
   │  ⚠ 2 homebrew characters (no art)      │
   │                       [ Cancel ][ Add ]│
   └────────────────────────────────────────┘
   ```
   Newly imported scripts sort to the **top** of the list with a "new" badge and
   an explicit success snackbar. Fixes #35, #36.

### S9. Home, sessions and the record

```
┌──────────────────────────────────────────────┐
│                 Clocktower                   │
│                  Grimoire                    │
│  ──────────────────────────────────────────  │
│  ┌────────────────────────────────────────┐  │
│  │ ▶ RESUME                               │  │
│  │   Bad Moon Rising · 12 players         │  │
│  │   Night 3 · saved 4 minutes ago        │  │
│  └────────────────────────────────────────┘  │
│  [ New game (storyteller) ]                  │
│  [ Take notes (player) ]                     │
│  [ Character library & night order ]         │
│  ── past games ────────────────────────────  │
│   BMR · 12p · evil won · 24 Aug   [open][↥]  │
│   TB  · 8p  · good won · 19 Aug   [open][↥]  │
│  ──────────────────────────────────────────  │
│  build a1b2c3d · up to date                  │
└──────────────────────────────────────────────┘
```

- **`New game` guard (fixes #1):** when `game != null`, tapping New game shows
  *"Bad Moon Rising, night 3, is still in progress. Starting a new game archives
  it."* → `[Archive & start new] [Resume instead] [Cancel]`. The wizard's final
  action repeats the warning in its label.
- **Archive (fixes #38):** `SavedData.archivedGames: List<GameState>` (keep the
  last 10, LRU). `endGame()` and `startGame()` both archive rather than delete.
  An archived game opens read-only into `RevealSheet` + `GameLogDialog`, and gets
  a "Share summary" action producing plain text:
  ```
  Bad Moon Rising · 12 players · 24 Aug 2026 · EVIL WINS
  1 Uri     Chambermaid   executed D2
  2 Dana    Lunatic (shown Zombuul)  survived
  …
  N1  Ari died in the night
  D1  Uri nominated Dana — 6 votes, reached the block
  ```
- **Timestamp on the resume card (fixes #45):** relative-format `updatedAt`.
- **PWA storage durability (fixes #39):** call `navigator.storage.persist()`
  once at boot; if it returns false, show a one-time Home banner ("Add to Home
  Screen so iOS keeps your saved game"). Surface `WebStore.save` failures
  instead of swallowing them (`WebApp.kt:32-34`) — a red "couldn't save" chip in
  the top bar.
- **Build + update on web (fixes #40):** pass `buildLabel` in
  `Main.kt:180-198` (stamp it the same way `index.html:43` already is), and add
  a web `UpdateBanner`: register the service worker with
  `registration.addEventListener('updatefound', …)`, and when the new worker
  reaches `installed` with a controller present, show "New version ready —
  [Reload]". Drop the unconditional `skipWaiting()` (`sw.js:18`) in favour of a
  `SKIP_WAITING` message from that button, and keep the previous cache until the
  new one is fully populated (fixes #44).
- **Wake lock (fixes #41):** re-request on
  `document.addEventListener('visibilitychange', …)` when visible, and hoist
  `KeepScreenOn()` from `GameShell` to the app root.
- **Manifest (fixes #42, #43):** drop `"orientation": "portrait"` (or use
  `"any"`), add 192/256/384 icons, an `"id"`, and
  `"shortcuts": [{"name":"Resume game","url":"./?resume=1"}, {"name":"New game","url":"./?new=1"}]`.

### UI text (storyteller voice, imperative, short)

- New-game guard: **"Bad Moon Rising is still in progress (night 3). Starting a
  new game will archive it."**
- Checklist header: **"Before the first night — 3 of 9 done"**
- Lunatic bluffs: **"Pick 3 bluffs for the Lunatic. These are separate from the
  Demon's, and may include characters that are in play."**
- Snitch: **"The Snitch gives each Minion 3 bluffs. Pick Ari's three — they can
  differ from Sam's."**
- Evil Twin: **"Pick the good player who is the Evil Twin's twin. They will see
  each other tonight."**
- Ogre on the hand-out card: **"Show the Ogre their token only. They never learn
  their alignment."**
- Traveller hand-out: **"Tell \<name\> they are a Traveller and whether they are
  good or evil."** → `[Good] [Evil]`
- Bag, choice bracket: **"Godfather: play 1 or 3 Outsiders — which are you
  running?"** `[1] [3]`
- Kazali: **"Kazali: leave the Minions out of the bag. They are chosen on the
  first night."**
- Lil' Monsta: **"Lil' Monsta is a token, not a seat. Deal 1 extra Minion and no
  Demon."**
- Import failure on a plain URL: **"That link points to a script page, not a
  script. Open it, download the JSON, then use Import from file."**

### Data / resource changes

- `GameState`: `bluffSets: Map<String, List<String>>` (replacing
  `demonBluffIds`, with migration); `setupChoices: Map<String, String>` for
  Xaan's X, the Outsider-count branch, Alchemist/Amnesiac notes.
- `Player`: `isTraveller` already exists — honour it in the setup wizard;
  add `tokenShownAt: Long?` and `alignmentToTell: Alignment?`.
- `SavedData`: `recentRosters`, `archivedGames`.
- `Setup.modifierFor`: return **all** legal branches rather than silently picking
  `matches.last()` (`Setup.kt:203-208`); the UI asks which branch is being played
  and stores it in `setupChoices`.
- `characters.json`: `lilmonsta` and `kazali` need a machine-readable flag for
  "no Demon seat" / "no Minions in bag" — either extend the bracket parser
  (`Setup.kt:131-133` already special-cases `"No Demon"`) or add an explicit
  `bagRule` field. Do **not** rely on prose.
- `night_and_jinxes.json` / `NightOrder`: the ordering is already right —
  `snitch` sits at first-night index 15, between `MINION_INFO` (14) and
  `DEMON_INFO` (18) — but the step is generated by the generic branch
  (`NightOrder.kt:142-178`), so it is **text only**: it prints the
  `firstNightReminder` prose and offers no way to pick or show each Minion's
  three bluffs. Same for the Lunatic (index 16): the prose is complete, the
  inputs do not exist. Both need bluff-set-aware steps that read
  `bluffSets["minion:<id>"]` / `bluffSets["lunatic"]` and offer a
  `BluffsCard` per recipient, exactly as `DEMON_INFO` does today
  (`NightScreen.kt:783-786`).
- No changes needed to `night_guide.json` — its prose is already correct and
  should be surfaced in the character detail sheet (S7).

---

## Tests to add

Engine (`engine/src/test/kotlin/...`) — all of these fail today:

1. **Bluff sets are independent.**
   Given a game with a Demon and a Lunatic;
   When `setBluffs("demon", [saint, fool, tinker])` and
   `setBluffs("lunatic", [chambermaid, courtier, exorcist])`;
   Then `bluffSets["demon"]` is unchanged and both sets survive a
   serialize/deserialize round trip.

2. **Lunatic bluffs may include in-play characters.**
   Given the Chambermaid is in play;
   When the Lunatic bluff candidate list is computed;
   Then `chambermaid` is present and flagged `inPlay = true`
   (the Demon's list must still exclude it).

3. **Snitch raises one bluff task per Minion.**
   Given a Snitch, a Baron and a Poisoner in play;
   When `SetupTasks.activeFor(state)` runs;
   Then it contains `snitch.bluffs.<baronSeat>` and `snitch.bluffs.<poisonerSeat>`,
   both `required`, and `validateSetupState` reports both while they are empty.

4. **`demonBluffIds` migrates.**
   Given a saved `GameState` JSON with `demonBluffIds:["saint","fool","tinker"]`
   and no `bluffSets`;
   When it is deserialized;
   Then `bluffSets["demon"] == ["saint","fool","tinker"]`.

5. **Evil Twin task requires an opposite alignment.**
   Given an Evil Twin;
   Then the `eviltwin.twin` candidate list contains only good players, and
   `validateSetupState` reports "choose the Evil Twin's twin" until the TWIN
   reminder is placed on exactly one of them.

6. **A flipped Ogre's hand-out card is not evil-coloured.**
   Given an Ogre whose `alignmentFlipped == true`;
   When the hand-out card model for that seat is built;
   Then `nameColour == team(OUTSIDER)` and `alignmentToTell == null`.

7. **Travellers do not count toward the bag.**
   Given 12 seats, one marked `isTraveller`;
   Then `Setup.distributionFor` is called with 11, `validateBag` expects an
   11-character bag, and `GameActions.deal(state, bagOf11)` succeeds.

8. **Kazali bags validate with zero Minions.**
   Given a 12-player Kazali bag (7 TF / 2 OUT / 0 MIN / 1 Kazali) with the
   `kazali.noMinions` acknowledgement recorded;
   Then `validateBag` returns no issues.
   *(Today: "Minion: 0 in bag, expected 2".)*

9. **Lil' Monsta bags validate with no Demon seat.**
   Given a 12-player Lil' Monsta bag with 3 Minions and no Demon character, and
   the `lilmonsta.noDemonSeat` acknowledgement;
   Then `validateBag` returns no issues and `deal` assigns 12 non-Demon tokens.

10. **The Sentinel is honoured by the bag builder.**
    Given `fabledIds = ["sentinel"]` and a 12-player bag with 3 Outsiders;
    Then `validateBag(bag, 12, fabledIds)` is empty **and** the setup screen's
    "Need:" line offers 2 or 3 Outsiders.

11. **Choice brackets expose every branch.**
    Given a bag containing the Godfather;
    Then `Setup.legalOutsiderCounts(12, bag) == setOf(1, 3)` and the header
    model exposes both, not just `matches.last()`.
    *(Today `adjustedDistribution` returns only the +1 branch.)*

12. **`randomBag` honours pins and bans.**
    Given `pinned = ["drunk"]`, `banned = ["mastermind"]`;
    Then every generated bag contains the Drunk, none contains the Mastermind,
    and the result is still a legal distribution.

13. **Script ids are content-derived.**
    Given two different scripts, both with no `_meta` name;
    When both are imported;
    Then `importedScripts.size == 2`.
    *(Today the second replaces the first — `Script.kt:74-82`.)*

14. **Starting a new game archives the old one.**
    Given a game in progress at night 3;
    When `startGame(...)` is called;
    Then `archivedGames.first().cycle == 3` and the archived log still contains
    every death and nomination.

15. **A resurrected / re-identified player is re-flagged for hand-out.**
    Given a player whose token was shown (`tokenShownAt != null`);
    When `assignCharacter` or `setShownCharacter` changes their identity;
    Then `tokenShownAt == null` and the checklist raises "\<name\> needs a new
    token".

16. **Setup tasks are phase-independent.**
    Given a Pit-Hag turns a player into the Drunk on night 3;
    Then `SetupTasks.activeFor(state)` includes `drunk.shown` for that seat.
    *(Today the prompt is gated on `phase == SETUP`, `GameShell.kt:380`.)*

UI checks runnable via `./gradlew -p tools/uicheck compileKotlin` (not in this
phase): the setup screen compiles with `KeyboardOptions`/`FocusRequester`
imports, and `ReferenceScreen` accepts a nullable `GameState`.
