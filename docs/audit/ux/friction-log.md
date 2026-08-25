# End-to-end friction log (ux/friction-log) — one BMR game, moment by moment

This is a **playthrough audit**, not a character audit. It walks one complete
12-player Bad Moon Rising game (Night 1 → Day 3) strictly through the code
paths a storyteller's fingers actually travel — `SetupScreen` → `RevealFlow` →
`GameShell` → `NightScreen` → `DayScreen` → `SeatSheet` — and records, at every
single moment: what the screen shows, what has to be tapped, what has to be
held in the storyteller's head or written on paper, what the app gets wrong,
and what it should have shown instead. Two shorter passes follow for Trouble
Brewing and Sects & Violets.

The rules are **not re-derived here**. Every defect is confirmed against the
character auditors' files under `docs/audit/characters/`, which cite the wiki;
each row names the file and defect number. Where a defect is new to this log
(mostly compound/ordering failures that only appear when several characters
interact in one night), it is marked **[new]**.

---

## Official rules (sources)

Rules ground truth for this log is the set of character audits already written
against the official wiki, plus the app's own data files where they *assert* a
rule:

| Scope | Source used |
| --- | --- |
| Pukka poison-then-kill, Exorcist-on-Pukka, protection clears poison | `docs/audit/characters/pukka.md` |
| Devil's Advocate "different to last night", survives-execution | `docs/audit/characters/devilsadvocate.md` |
| Godfather setup choice, "Outsiders that die **at night** don't count" | `docs/audit/characters/godfather.md` |
| Lunatic's own bluffs / fake Minions / acting as the believed Demon | `docs/audit/characters/lunatic.md` |
| Gossip statement capture, unprotected victim, dead/poisoned Gossip | `docs/audit/characters/gossip.md` |
| Professor Townsfolk-only, dawn announcement, first-night re-run | `docs/audit/characters/professor.md` |
| Courtier 3-night countdown, character (not seat) choice | `docs/audit/characters/courtier.md` |
| Sailor immortality voided by droison; execution path | `docs/audit/characters/sailor.md` |
| Tea Lady alive-neighbour skipping, two CANNOT DIE tokens | `docs/audit/characters/tealady.md` |
| Exorcist "different to last night", Demon-branch resolution | `docs/audit/characters/exorcist.md` |
| Grandmother grandchild validation, death cause, impairment | `docs/audit/characters/grandmother.md` |
| Chambermaid "who actually woke tonight" | `docs/audit/characters/chambermaid.md` |
| Fool first-death save, spent-ness, protection precedence | `docs/audit/characters/fool.md` |
| Po charge / 3 attacks (the Lunatic's believed Demon) | `docs/audit/characters/po.md` |
| Imp star pass, Scarlet Woman precedence | `docs/audit/characters/imp.md`, `scarletwoman.md` |
| Virgin, Slayer, Butler day mechanics | `docs/audit/characters/virgin.md`, `slayer.md`, `butler.md` |
| Vortox no-execution win, false info everywhere | `docs/audit/characters/vortox.md` |
| Cerenovus madness payload and enforcement | `docs/audit/characters/cerenovus.md` |
| Snake Charmer alignment swap, token migration | `docs/audit/characters/snakecharmer.md` |
| Juggler guesses and scoring, Savant statements | `docs/audit/characters/juggler.md`, `savant.md` |
| Night order positions | `engine/src/main/resources/botc/data/night_and_jinxes.json` (`firstNight`, `otherNight`) |

Where the app's own prose contradicts those sources it is called out as a
**data defect** (see F-data rows and principle §11).

---

## What the app does today

Five surfaces, and the honest one-line verdict for each.

| Surface | File | What it does | Verdict |
| --- | --- | --- | --- |
| Setup | `SetupScreen.kt:63-134` (3 stages), `GameActions.validateBag` `GameActions.kt:420-496`, `validateSetupState` `GameActions.kt:503-561` | Script → names → bag with live distribution check; re-validated at "Begin night" (`GameShell.kt:133-140`) | Works, except setup *choices* (Godfather ±1, Grandmother's grandchild) are neither offered nor validated |
| Reveal | `RevealFlow.kt:39-131` | Pass-the-phone YOU ARE per seat, using `characterShownToPlayerId` | Works for the token; carries none of the Lunatic's illusion kit |
| Night | `NightScreen.kt:78-185` (sheet), `:690-765` (row), `:768-934` (detail), `:191-357` (tool tray), `NightOrder.kt:40-208` (sheet build) | Ordered checklist; per-step guide prose, show-card chips, four hard-coded resolvers, `InfoCalc` true info, and a token tray | The sheet is right; the *resolution* of almost every step is manual |
| Day | `DayScreen.kt:54-278` | Nomination → tap-to-vote → threshold → block banner → Execute | Voting is excellent. Nothing else about the day exists |
| Grimoire / seat | `GrimoireScreen.kt:327-494`, `SeatSheet.kt:59-384` | Circle with tokens; per-seat kill/revive/reminders/notes/character change | The universal escape hatch — and therefore where all the real bookkeeping happens |

Only **four** night steps have a resolver: `snakecharmer`, `fanggu`,
`professor`, and "any character on the Demon team" (`NightScreen.kt:462-525`).
Everything else in the game is prose plus a tray of tokens
(`NightScreen.kt:283-354`). That single fact generates most of this log.

`GameState` (`GameState.kt:93-115`) stores players, reminders, deaths,
nominations, `nightStepsDone: Set<String>`, and one free-text
`storytellerNotes`. It stores **no night choices, no day statements, no
per-effect durations and no pending consequences.** That single fact generates
the rest.

---

## Defects and gaps

### The game under test

**Script** Bad Moon Rising. **12 seats.** Bag = 8 Townsfolk / 1 Outsider /
2 Minions / 1 Demon — legal for 12 with the Godfather's `[-1 or +1 Outsider]`.

| Seat | Player | Character | Team |
| --- | --- | --- | --- |
| 1 | Ana | Devil's Advocate | Minion |
| 2 | Ben | Sailor | Townsfolk |
| 3 | Cleo | Chambermaid | Townsfolk |
| 4 | Dev | Gossip | Townsfolk |
| 5 | Erin | Grandmother | Townsfolk |
| 6 | Gita | Professor | Townsfolk |
| 7 | Iris | Tea Lady | Townsfolk |
| 8 | Hal | Exorcist | Townsfolk |
| 9 | Finn | **Fool** — the Grandmother's grandchild | Townsfolk |
| 10 | Jonas | **Lunatic**, believes they are the **Po** | Outsider |
| 11 | Kai | **Pukka** | Demon |
| 12 | Lena | Godfather | Minion |

*Roster note.* The user's roster names twelve characters **plus** a Fool
grandchild — thirteen characters for twelve seats, which no legal Godfather bag
can hold (12 is 7/2/2/1; ±1 Outsider gives 8/1/2/1 or 6/3/2/1). The Fool is
kept (the user named the grandchild explicitly, and the Fool + Godfather +
Grandmother collision at F43 is one of the most instructive moments in the
game); the **Courtier** is logged separately as **Table D**, since its friction
is night-order-independent and reproduces unchanged in this game.

**Events.** Pukka poisons the Sailor N1 and the Gossip N2. DA protects Lena
both N1 and N2. Erin (Grandmother) is executed D1 and resurrected by the
Professor on N2. Jonas (Lunatic) "kills" three players on N2 believing he
charged as the Po. The Gossip makes a true statement D2. Jonas is executed D2
(Outsider death → Godfather armed). Hal (Exorcist) picks the Pukka N3; the
Godfather shoots Finn (Fool/grandchild) N3.

---

### Table A — Bad Moon Rising, Setup → Day 3

Legend: **taps** counts deliberate touches (chips, buttons, checkboxes, dialog
dismissals), excluding scrolling. P0 = wrong outcome/rules broken · P1 = manual
bookkeeping the app could do · P2 = missing clarity · P3 = polish.

| # | Moment | Screen (file:line) | Taps | ST must remember / write elsewhere | Defect | Should be |
| --- | --- | --- | --- | --- | --- | --- |
| **F1** | Build the bag: choose the Godfather's −1 Outsider | `SetupScreen.kt:365-384` header, `Setup.kt:203-208`, `GameActions.kt:443` | 12 checkboxes + search | Which Godfather option you took — forever | **P2** The "Need:" line reads *6 townsfolk · 3 outsiders* because `modifierFor` silently adopts the **last** listed option (`+1`) as the default, while `validateBag` accepts both. The ST's legal 8/1/2/1 bag contradicts the header with no error and no explanation. Nothing records the choice (`godfather.md`#10) | A two-way chooser — "Godfather: −1 Outsider (8/1/2/1) · +1 Outsider (6/3/2/1)" — stored in state and echoed on the Godfather's first-night step |
| **F2** | Deal & pass the phone round the circle | `SetupScreen.kt:110-118` → `RevealFlow.kt:39-131` | 24 (2/seat) | — | Works: Jonas sees the **Po** token and the **Po's** ability text (`RevealFlow.kt:54,118-122`) | — |
| **F3** | "The Lunatic is in play — which Demon token do they see?" | `GameShell.kt:416-440` | 2 | — | **P2** Lists every Demon on the script equally; the default rule is that the Lunatic thinks they are *the Demon in play* (`lunatic.md`#8) | Pre-select the in-play Pukka; one line explaining the "different Demon" variant |
| **F4** | Place the Grandmother's grandchild | — (no prompt exists) | 5 (Grimoire → Finn → Add reminder → Grandmother → Grandchild) | **That the grandchild exists at all** | **P1** `validateSetupState` (`GameActions.kt:503-561`) validates the Drunk's token, the Lunatic's Demon, the Marionette's token *and neighbour*, and exactly one good Fortune Teller red herring — and has **no Grandmother case** (`grandmother.md`#5). You can start the first night with no grandchild anywhere | A setup dialog exactly like the red-herring one (`GameShell.kt:347-376`), constrained to good, non-Grandmother seats |
| **F5** | Choose 3 demon bluffs | Menu → `BluffsSheet.kt:35-112` | 4 | **The Lunatic's three bluffs, on paper** | **P0** There is exactly one bluff list (`GameState.kt:102`) and `BluffsSheet.kt:40-45` filters out in-play characters. The Lunatic needs their **own** three, which **may be in play** (`lunatic.md`#2) | Two named bluff sets — "Demon bluffs" and "Lunatic bluffs" — the second allowing in-play characters |
| **F6** | Tap "Begin night" | `GameShell.kt:126-140`, `:551-591` | 1 | — | Works — the setup guard re-runs `validateSetupState` and is advisory, not blocking | — |
| **F7** | N1 · Dusk | `NightOrder.kt:58` | 1 | — | Works | — |
| **F8** | N1 · Minion info | `NightOrder.kt:60-80` | 1 | — | Works — names Ana + Lena and points at Kai | — |
| **F9** | N1 · **Lunatic** (firstNight 16) | `NightScreen.kt:768-832`, `characters.json` lunatic, `night_guide.json` lunatic.first | ~7 + typing | (a) **which two players** you pointed at as fake Minions — for the whole game; (b) **which three good tokens** you showed; (c) that the Po does not act on night 1 | **P0** Nothing computes the fake-Minion count (2), offers a picker, or records the choice (`lunatic.md`#3). The "Show the Lunatic" card is `token:"pick"` so you search for the Demon the app already stores in `shownCharacterId` (`lunatic.md`#11). Whole conditional wall of text ("If the token received by the Lunatic is a Demon that would wake tonight…") is left for the ST to evaluate | A Lunatic panel: "Fake Minions (2): [chips]" placing `lunatic:Fake minion` tokens; "Lunatic bluffs: X, Y, Z [Show]"; and "The Po does not act on the first night — nothing to do tonight" |
| **F10** | N1 · Demon info | `NightOrder.kt:81-119`, chip `NightScreen.kt:783-788` | 2 | — | Works well — names Minions, lists the chosen bluffs, **and** appends "Also show the Demon who the LUNATIC is (Jonas)" | — |
| **F11** | N1 · **Sailor** (20) | `NightScreen.kt:191-357` tray only | 3–4 | That *you* choose whether Ben or the target is drunk; who you chose (for tomorrow's "last night") | **P1** No resolver — the entire character is an ST decision the app never asks for (`sailor.md`#4). The tray's seat row is `state.players` unfiltered, so dead seats are selectable (`NightScreen.kt:314-352`, `sailor.md`#5) | "Ben points at ⟨seat⟩ · who is drunk until dusk? [Ben] [⟨target⟩]" — one tap places the token and records the choice |
| **F12** | N1 · **Godfather** (32) — show the Outsiders | `night_guide.json` godfather.first, `NightScreen.kt:802-831` → `GuideShowDialog` `:366-454` | ~7 + typing | Which Outsiders are in play | **P1** The one pure grimoire lookup this character gets is not computed — `godfather` is absent from `InfoCalc.supports` (`InfoCalc.kt:29-36`) — and the show card is `token:"pick"`, i.e. a search box (`godfather.md`#3) | "Outsiders in play: **Lunatic** (Jonas) — [Show each]" |
| **F13** | N1 · **Devil's Advocate** (34) | tray only | 3–4 | **Who you chose** — the app is about to delete it | **P1** No resolver, no "chose nobody", dead seats offered (`devilsadvocate.md`#5,#6) | A picker over *living* seats that records the choice |
| **F14** | N1 · **Pukka** (41) — the headline | `NightScreen.kt:518-523` → `DemonKillPanel` `:534-638` | 4 (and one wrong button one tap away) | That the Pukka **poisons** on night 1 and kills nobody | **P0** The step renders **"Demon kill — who did Kai choose?"** with a live "*Ben dies*" button — on night 1, when the Pukka may only poison. The Demon branch is not gated on `state.cycle` and never on the character (`pukka.md`#1,#3). The correct act — place `pukka:Poisoned` on Ben — is only reachable through the tray | "Who does the Pukka poison? → [Ben]" places the token and shows a standing line: **"Ben dies at the Pukka's next wake."** No kill button on night 1 |
| **F15** | N1 · **Grandmother** (53) | `NightScreen.kt:836-861` + `InfoCalc.revealCharacter` | ~7 | — | **P2** `targetsNeeded("grandmother")==1` so the ST taps a seat from a chip row of **all 12 players, including evil ones and Erin herself**, when the `Grandchild` token already answers the question (`grandmother.md`#6) | Default the target to the Grandchild token holder; warn on anything else |
| **F16** | N1 · **Chambermaid** (70) picks Gita + Jonas | `InfoCalc.kt:465-482` | 4 | — | Correct here **by luck**: `professor` is absent from `firstNight` (→ "doesn't wake" ✓) and `lunatic` is present at 16 (→ "wakes" ✓). Headline still literally says **"(approximate)"** and hands the work back (`chambermaid.md`#10) | A computed, unhedged number derived from what the sheet actually *does* tonight |
| **F17** | N1 · Dawn | `NightOrder.kt:59` | 1 | That nobody died, and to say so | **P1** DAWN is a constant string, "Announce who died." The app knows the answer (`state.deaths` filtered by cycle) and does not say it | A dawn report: deaths (with cause), resurrections, silent saves, and the exact lines to say/not say |
| **F18** | Tap "Dawn" | `GameShell.kt:147-161`, `:618-659` | 1–2 | — | **P2** Blocks on unticked steps — good — but the sheet includes rows that can never do anything tonight, training the ST to tick blindly | Steps that cannot fire tonight should be auto-marked `skipped (reason)`, not "unfinished" |
| **F19** | **Day 1 opens** | `DayScreen.kt:85-124` | — | Ben is Pukka-poisoned all day · Lena survives execution today · Finn is the grandchild · Jonas thinks he is the Po · the Tea Lady currently protects Gita and Hal | **P1** There is **no day briefing anywhere in the app**. `GameShell.requestPhaseAdvance` only switches tabs (`GameShell.kt:162-168`); the Day tab shows an alive count and a vote threshold. Confirmed independently by `devilsadvocate.md`#3, `courtier.md`#9, `fool.md`#5, `slayer.md`#7, `butler.md`#7, `virgin.md`#10, `savant.md`#2 | A "Today" card computed from state: impairments with durations, protections, madness, spent abilities, pending public actions |
| **F20** | D1 · Nominations & votes | `DayScreen.kt:126-255` | ~6 per nomination | — | Works, and works well: clockwise vote order from the nominee (`:166-172`), ghost votes spent on record (`:232-240`), tie/beat logic (`GameState.kt:141-152`) | — |
| **F21** | D1 · Execute Erin | `DayScreen.kt:111-114` | 1 | — | **P0** `viewModel.kill(id, EXECUTION)` with **no** `StatusEffects.deathNotes` call. Right outcome here only because Erin has no protection; the identical button would have killed Lena (`devilsadvocate:Survives execution`) silently. Three unchecked kill paths exist: `DayScreen.kt:111-114`, `DayScreen.kt:350-357`, `GameShell.kt:598-605` (`devilsadvocate.md`#1, `fool.md`#1, `sailor.md`#2, `tealady.md`#5) | One `attemptDeath()` funnel that returns Killed / Prevented(reason) / AlreadyDead and is used by every button |
| **F22** | D1 · Tap "Dusk" | `GameShell.kt:141-146` guard, `GameActions.kt:231-242,261-262` | 1–2 | **Who the DA chose last night** | **P0** `EXPIRES_AT_DUSK` deletes `devilsadvocate:Survives execution` at the DAY→NIGHT boundary — i.e. **before** the DA's night-2 step (otherNight 22). The app destroys the only record of last night's choice just before it needs it (`devilsadvocate.md`#2). Same shape for `exorcist:Chosen`, swept at dawn (`GameActions.kt:221`) | Choice history in state; token expiry is a *display* rule, not a memory rule |
| **F23** | N2 · **Sailor** (9) | tray | 3–4 | That Ben is poisoned, so his choice does nothing **and his immortality is gone** | **P1/P0** The step says neither. `StatusEffects.kt:73` (`if (id == "sailor" && player.alive)`) will assert "The Sailor can't die" a few steps later regardless (`sailor.md`#1,#10) | "Ben is POISONED — his choice has no effect and **he can die tonight**" |
| **F24** | N2 · **Devil's Advocate** (22) — *the user's complaint* | `NightScreen.kt:308-354`; alt path `SeatSheet.kt:109-117` | 3–4 | Last night's target | **P0** The tray offers Lena again with no marking and re-places the token happily. Worse, the **other** placement path — Grimoire → seat → Add reminder — calls plain `GameActions.addReminder` (`SeatSheet.kt:113` → `GameActions.kt:186`), so two live "Survives execution" tokens can exist at once. That path-dependence is exactly the reported "DA wasn't automatically removed" (`devilsadvocate.md`#2,#4) | Living-seats-only picker with **last night's target struck through and disabled**; one exclusive token; auto-cleared at the right moment |
| **F25** | N2 · **Lunatic** (31) — three fake kills | tray only; `characters.json` lunatic reminders = `Attack 1/2/3` | **6+** (3 × chip + 3 × seat, scrolling a 12-seat `LazyRow` each time) | Whether a Po-Lunatic is even *entitled* to 3 tonight; which Demon's rules apply; that nobody actually dies | **P0** `QuickResolutions` requires `team == Team.DEMON` (`NightScreen.kt:520`) and the Lunatic is an Outsider, so **there is no picker at all**. The Po's charge is unmodelled: the engine's own playtest uses `lunatic:"3 attacks"` (`FullGamePlaytestTest.kt:1149`), a label that does not exist in `characters.json`, so it can be placed from **no picker in the app** (`lunatic.md`#1,#6, `po.md`#10) | The Lunatic's panel is generated from `shownCharacterId`: Po ⇒ "choose 1, or none (charges 3 for tomorrow)"; a charged Po ⇒ "choose 3". One tap per target places the Attack tokens; the charge persists across dawn |
| **F26** | N2 · **Exorcist** (32) picks Lena | prose + tray | 3–4 | Whether Lena is the Demon (she is not); who was picked, for tomorrow's "different" rule | **P1** The app knows who the Demon is and still renders the raw conditional sentence, leaving the ST to check the grimoire in the dark (`exorcist.md`#3) | "Lena is **not** the Demon — nothing happens tonight." / "Kai **is** the Demon — [Show the card & silence them]" |
| **F27** | N2 · **Pukka** (39) — poison moves, Ben dies | `NightScreen.kt:534-638`; `StatusEffects.kt:73`; `GameActions.kt:153` | **6–8** across two screens | Kill Ben *before* moving the poison (so `abilityImpairedAtDeath` snapshots true); remove Ben's poison; place Dev's; place `pukka:Dead`; remember Dev is poisoned all day tomorrow | **P0 ×3.** (a) The panel asks "who did Kai choose?" — answering it truthfully ("Dev") and tapping "Dev dies" **kills the wrong player on the wrong night** (`pukka.md`#1). (b) The pending victim is invisible and is destroyed the moment the next poison token is placed, because there is one exclusive token (`GameActions.kt:194-201`, `pukka.md`#4). (c) Selecting Ben prints **"! The Sailor can't die."** unconditionally (`StatusEffects.kt:73`) even though Ben is poisoned — the app argues for the wrong ruling (`sailor.md`#1) | One atomic action: "Pukka poisons **Dev** → **Ben** dies now (poisoned at death)." The engine kills Ben with the poison still on, clears it, moves the token, and files the dawn line |
| **F28** | N2 · **Godfather** (56) | prose only | 1 | Whether anyone who died today was an Outsider | **P1** The step appears every night with no condition evaluated, although `state.deaths` carries `day`, `atNight` and `characterIdAtDeath` (`GameState.kt:77-90`) — everything needed. Nothing places `godfather:"Died today"`; the string appears in `characters.json` and in **no code at all** — the only `godfather` reference under `app/src` + `engine/src/main` is `StatusEffects.kt:116` (`godfather.md`#2,#5) | "No Outsider died today — the Godfather does not wake." (auto-skipped, reason shown) |
| **F29** | N2 · **Gossip** (57) | prose only | 1 | Whether the Gossip said anything on Day 1 | **P1** No picker, no protection check, no kill button — although the official instruction is literally "choose a player **not protected from dying tonight**", which `StatusEffects.deathNotes` already computes (`gossip.md`#3) | "No statement recorded for Day 1 — nothing happens." |
| **F30** | N2 · **Professor** (63) resurrects Erin | `NightScreen.kt:499-517` | 2 | That the target must be **Townsfolk**; that the Professor must be sober; to place the `Alive` token; **to announce it at dawn**; **to re-run Erin's first night** | **P0 ×4.** The picker offers *every* dead player and always calls `resurrect` (`professor.md`#1). No `isImpaired` check, unlike `DemonKillPanel` at `:548` (`professor.md`#4). The `professor:Alive` token declared in `characters.json` is placed by no code (`professor.md`#5). **No dawn announcement and no first-night re-run** — the user's two headline complaints (`professor.md`#2,#3) | Townsfolk-only picker (with an explicit "not a Townsfolk — nothing happens, ability spent" branch); impairment gate; `Alive` token; a dawn line **"Erin is alive again"**; and an inserted *Grandmother · first night* row on tonight's sheet |
| **F31** | N2 · **Grandmother** (71) | prose only | 1 | That Erin needs her first-night info re-run — and that this row is **not** it | **P0/P1** The row is the "did the grandchild die?" check, rendered as a conditional the ST must evaluate (`grandmother.md`#3). The row Erin actually needs tonight cannot exist: the sheet is built once from the global order list (`NightOrder.kt:40-208`) with no insertion path (`professor.md`#3) | "Finn (grandchild) did not die tonight — nothing happens", plus a separate inserted row: "Grandmother · **first night info** (resurrected) — show Finn's token" |
| **F32** | N2 · **Chambermaid** (93) picks Dev + Erin | `InfoCalc.kt:465-482` | 4 | — | **P0** Membership test is `p.characterId in order`. `gossip` (otherNight 57) and `grandmother` (71) are both **in** the list, so the app answers **"2 of the 2 wake tonight"**. The app's *own guide* says "The Gossip does not wake" and "The Grandmother does not wake" (`night_guide.json`). True answer is **0** — or **1** if the ST re-ran Erin's first night, a decision the app has no way to record (`chambermaid.md`#1). Also: the picker lists all 12 seats including dead ones and the Chambermaid herself (`chambermaid.md`#6) | Count from what the sheet *did*: rows that woke a player **for their own ability**. Alive, non-self picker |
| **F33** | N2 · Dawn | `NightOrder.kt:59` | 1 | "Ben died." **and** "Erin is alive again." | **P1** Neither is shown. The resurrection appears nowhere except as "(later resurrected)" appended to Erin's Day-1 death line in the log (`GameExtras.kt:58-62`) | Dawn report, in order, with the resurrection called out separately as the rules require |
| **F34** | **Day 2 opens** | `DayScreen.kt:85-124` | — | Dev is poisoned · Lena survives execution · the Professor is spent · the Fool still has his save · the Tea Lady protects Gita and Hal · the Lunatic's three "kills" matched nothing | **P1** Same missing briefing as F19 | see F19 |
| **F35** | D2 · **the Gossip makes a true public statement** | **nowhere** | 0 — there is no control | The statement verbatim, whether it was true, and that Dev was poisoned when he said it | **P0** There is no day-time input of any kind. `DayScreen.kt:54-278` has no notes UI; the only text fields are the game-wide `storytellerNotes` blob (`GameShell.kt:685-706`) and a per-seat note (`SeatSheet.kt:366-372`), and **neither is displayed at the Gossip's night step**. This is the user's verbatim complaint ("Gossip was awful"), and they explicitly asked for it **even when the Gossip is not in play** (`gossip.md`#1,#5,#6) | A **public record** on the Day tab: `[+ Statement]` → speaker chip → text → true/false toggle, replayed verbatim at the Gossip step that consumes it |
| **F36** | D2 · Execute Jonas (Lunatic — an Outsider) | `DayScreen.kt:111-114` | 1 | **That an Outsider died today, so the Godfather may kill tomorrow night** | **P0/P1** No death notes on this path (F21). No `godfather:"Died today"` token is placed by any code. The one Godfather rule the app *does* implement — `StatusEffects.kt:116` — checks only `team == OUTSIDER` and `godfather.alive`, never the phase or the day, so it also fires on **night** Outsider deaths, which the rules exclude (`godfather.md`#1,#2) | On a confirmed Outsider **day** death: place `godfather:"Died today"`, and put "**Godfather may kill tonight**" in tonight's dawn-to-dusk briefing |
| **F37** | D2 · Dusk | `GameActions.kt:261-262` | 1 | — | Lena's "Survives execution" swept again; still no memory for N3 | see F22 |
| **F38** | N3 · **Sailor** (9) — Ben is dead | `NightOrder.kt:142-178` (no alive filter) | 1 | — | **P2** The step is emitted for a dead Sailor with the full tray, showing only a generic "All holders are dead — usually skip" (`NightScreen.kt:751-757`), and still blocks the dawn guard until ticked (`sailor.md`#8) | Auto-skip with a reason |
| **F39** | N3 · **Devil's Advocate** (22) — third pick | tray | 3–4 | Both previous targets | **P0** Still no memory; still cannot enforce "different to last night" | see F24 |
| **F40** | N3 · **Lunatic** (31) — Jonas is dead | `NightOrder.kt:157-172` | 1 | — | **P2** The row correctly greys out, but the **Pukka's** step still says *"LUNATIC (Jonas) is in play — wake them first for their fake attack"* because `NightOrder.kt:161` finds the seat without checking `alive` (`lunatic.md`#9) | Drop the annotation when the Lunatic is dead |
| **F41** | N3 · **Exorcist** (32) picks the Pukka | prose + tray | 3–4 | Who was picked on N2 (Lena) — the app deleted it at dawn | **P1** No "yes, that is the Demon" resolution (`exorcist.md`#3); no way to honour "different to last night" (`exorcist.md`#2). **Data defect:** the show card reads "THIS PLAYER STOPPED YOU TONIGHT" where the official info token is "THIS CHARACTER SELECTED YOU" (`night_guide.json` exorcist.other, `exorcist.md`#7) | "Kai **is** the Demon → [Silence the Demon & show the card]" — one button that places a cycle-scoped token, opens the correct card, and marks the Demon's step suppressed |
| **F42** | N3 · **Pukka** (39) — Exorcised, but Dev still dies | `NightOrder.kt:149-154` + `NightScreen.kt:518-523` | **~8** across two screens | That an Exorcised **Pukka** still kills its previously-poisoned victim | **P0 ×2.** The step now shows *"— EXORCIST chose them: the Demon does not act tonight."* **and, directly beneath it, a fully live "Demon kill — who did Kai choose?" panel with a working "X dies" button.** Both halves are wrong: the panel should not exist at all, and the sentence is wrong for the Pukka — the previously-poisoned Dev **still dies** ("The Pukka does not wake to attack tonight, but a player still dies because of the Pukka's attack during the previous night", `pukka.md`#2, `exorcist.md`#1). The app's own text tells the ST to skip a step the rules say must half-run | "Kai is Exorcised: the Pukka places **no new poison** tonight — but **Dev dies now** from last night's poison. [Resolve Dev's death]" |
| **F43** | N3 · **Godfather** (56) shoots Finn — Fool **and** grandchild | prose only → `SeatSheet.kt:266-307` | **~8** | That the Godfather is a **Minion, not the Demon**; that the Fool's save is now spent | **P0 ×3.** (a) No picker and no kill button on the step — the tray's "Dead" chip places a token that leaves the target alive (`godfather.md`#4). (b) The only kill route, seat sheet → "Died at night", hard-codes `DeathCause.DEMON` (`SeatSheet.kt:271`) although a Godfather kill is not a Demon kill (`gossip.md`#2 documents the same bug class). (c) The confirmation dialog then prints two notes: "Fool: the first time they die, they don't" ✓ **and** "Grandmother dies too if the Demon killed her grandchild" ✗ — wrong, because the Godfather is not the Demon and because the Fool did not die (`grandmother.md`#4). Tapping "Death prevented" (`SeatSheet.kt:304`) records **nothing**: no `fool:"No ability"`, no log line, no state — so the Fool will be offered the same save again (`fool.md`#3) | Godfather panel with a target picker; a death pipeline that reports "Finn survives — **Fool** (first death). Fool's ability is now spent" and marks it; the Grandmother note fires only for `cause == DEMON` and only if the Grandmother is alive and sober |
| **F44** | N3 · **Gossip** (57) — true statement, poisoned speaker, dead Gossip | prose only | 1 | The Day-2 statement; that it was true; that Dev was poisoned when he said it; that Dev died at step 39 tonight | **P1** All three facts are decisive and none is on the step. The step's text is the same conditional sentence it has shown every night, whether the Gossip is alive, dead, healthy or poisoned (`gossip.md`#4) | "Dev's Day-2 statement: *'…'* — you marked it TRUE. **But Dev was poisoned when he said it and died earlier tonight: no one dies from the Gossip.**" |
| **F45** | N3 · **Professor** (63) — spent | `NightScreen.kt:503-505` | 1 | — | **P2** The resolver hides but the row stays and must be ticked every remaining night (`professor.md`#8) | Auto-skip: "Professor's ability is spent" |
| **F46** | N3 · **Grandmother** (71) | prose only | 1 | — | **P1** as F31 | — |
| **F47** | N3 · **Chambermaid** (93) | `InfoCalc.kt:465-482` | 4 | — | **P0** as F32 | — |
| **F48** | N3 · Dawn | `NightOrder.kt:59` | 1 | "Dev died." And to say nothing about Finn | **P1** as F17/F33 | — |
| **F49** | **Day 3 opens** | — | — | The Fool's save should be spent but was never marked · **nobody is queued to die on night 4**, because the Exorcised Pukka placed no new poison | **P1** The second fact is unrepresentable: the app has no concept of a *pending consequence*, so the state that governs tomorrow night exists only in the ST's head | "Pending tonight: **none** — the Pukka placed no poison (Exorcised on N3)" in the day briefing |
| **F50** | D3 · The Tea Lady, all game | `night_and_jinxes.json` (absent from **both** order lists), `StatusEffects.kt:81-90`, `characters.json` tealady | 0 offered / ~4 per recompute | Recompute "are both alive neighbours good?" after **every** death — five times in this game (Erin executed, Erin resurrected, Ben died, Jonas executed, Dev died) | **P1 ×3.** She never appears on the night sheet at all — `tealady` is in neither `firstNight` nor `otherNight`, and the only code that mentions her is `StatusEffects.kt:81-90`. `characters.json` declares **one** `"Can not die"` label, so the tray's copy-count logic (`NightScreen.kt:319-339`) treats it as exclusive and can never mark both neighbours (`tealady.md`#3). And the adjacency uses raw seats: with a dead seat between the Tea Lady and her true alive neighbour, the app protects the corpse and says nothing about the living neighbour (`StatusEffects.kt:84`, `tealady.md`#1) | A derived, always-current protection: the engine recomputes alive neighbours on every death, places/removes both tokens, and shows "Tea Lady protection: **ON** — Gita, Hal" in the grimoire and the day briefing |
| **F51** | D3 · Execute Hal (a Tea Lady neighbour) | `DayScreen.kt:111-114` | 1 | That Hal cannot die | **P0** Killed outright, no dialog, no note. The seat-sheet path *would* have warned (`SeatSheet.kt:256-307`), so the outcome depends on which button the ST happened to use (`tealady.md`#5, `fool.md`#1) | Single death funnel (see F21) |
| **F52** | Any time · Game log | `GameExtras.kt:44-105` | 2 | Everything else | **P2** The log derives only from `state.deaths` and `state.nominations`. Absent: the Pukka's poison chain, the Professor's resurrection as an event, the Exorcist's block, the Lunatic's fake kills, the Gossip's statement, the DA's protections, the Godfather's shot, the Fool's silent save (`professor.md`#11, `fool.md`#9, `exorcist.md`#10, `imp.md`#7) | A real event log fed by the same actions that mutate state |

**Table A tally:** 21 P0 · 22 P1 · 8 P2 · 1 data defect.
**Taps to run three nights and three days: ≈160**, of which roughly 55 are pure
bookkeeping the app already has the state to perform, and at least **five** are
one tap away from a rules break the app is actively recommending (F14, F21,
F27, F42, F51).

---

### Table B — Trouble Brewing pass: Imp star-pass · Scarlet Woman · Virgin · Slayer · Butler

| # | Moment | Screen (file:line) | Taps | ST must remember / write elsewhere | Defect | Should be |
| --- | --- | --- | --- | --- | --- | --- |
| **T1** | N1 · Butler picks a Master | tray only (`NightScreen.kt:283-354`) | 3–4 | That the Butler may not pick themself | **P1** No `butler` branch in `QuickResolutions`, so the tray will happily place `Master` on the Butler's own seat (`butler.md`#4). **Data defect:** `night_guide.json` butler.first asserts *"This still applies even if the Butler is drunk or poisoned"* — an app-authored sentence that is **wrong** and will make STs rule incorrectly (`butler.md`#3) | Not-self picker; correct guide prose |
| **T2** | D1 · A Townsfolk nominates the **Virgin** | `StatusEffects.kt:152-157` → `DayScreen.kt:154-159` | 1 warning, then ~8 by hand | Whether the nominator is *really* a Townsfolk (the Drunk trap); that the ability is spent **either way**; that the day ends | **P0 ×5.** The app prints a sentence and stops. It never evaluates the nominator's team although it is one lookup away; never checks whether the Virgin is poisoned (a poisoned Virgin's ability does not fire — `StatusEffects.isImpaired` sits in the same file at `:36-46`); never places `virgin:"No ability"`; never ends the day — `aboutToDie` keeps returning the previously blocked player and **both Execute buttons plus the dusk guard stay live** (`GameActions.kt:296-306`, `DayScreen.kt:111-114`, `:350-357`, `GameShell.kt:592-616`); and the vote panel stays open although the nomination ends before any vote (`virgin.md`#1-#6) | A nomination resolver: evaluate the nominator's true team → execute or not → spend the ability → close the day → record `WITHDRAWN` |
| **T3** | D1 · Slayer publicly shoots | **nowhere** | 5 (Grimoire → seat → Add reminder → Slayer → "No ability") | Whether the target is an **alive Demon**; whether the Recluse registers; that the shot is spent even on a miss or while droisoned | **P0 ×3.** The single most game-deciding day action in TB has zero UI (`slayer.md`#1). The generic "Mark spent" chip exists only inside `NightToolTray`, whose character comes from the expanded **night** step (`NightScreen.kt:98-100,263-279`) — the Slayer has no night step, so **the chip can never render** (`slayer.md`#2). Nothing enforces once-per-game (`slayer.md`#3). A slay must be `DeathCause.STORYTELLER`, which the log renders as "died (storyteller)" — indistinguishable from ST fiat (`GameExtras.kt:58`, `slayer.md`#6) | A Slayer action on the Day tab: target picker → "⟨X⟩ is/is not the Demon" (with an explicit Recluse-registration prompt) → resolve → spend → log |
| **T4** | D1 · The Butler votes without their Master | `DayScreen.kt:183-196` | 1 | Who the Master is (only visible on the *Master's* grimoire seat) | **P1** The tally knows nothing about the `Master` token; you can tap the Butler and Record an illegal vote onto the block with no warning, and you must switch tabs mid-nomination to find out who the Master even is (`butler.md`#1,#2) | Butler chip disabled unless the Master's chip is selected, with the reason on the chip; Master shown in the vote row |
| **T5** | N2 · Imp chooses itself → star pass | `NightScreen.kt:591-622`, `GameActions.kt:79-96`, `StatusEffects.kt:105` | 2 | That the heir must be an alive **Minion**; that the Scarlet Woman takes precedence; that Travellers don't count toward 5 | **P0 ×3.** The heir list is **every alive player** — Townsfolk, Outsiders, **Travellers** — and `starPass` validates nothing (`imp.md`#1). The Scarlet Woman is merely *sorted first* with the title "if able": a mandatory rule rendered as a hint (`imp.md`#2, `scarletwoman.md`#5). The 5-alive test counts Travellers although `GameState.aliveNonTravellers` exists at `GameState.kt:117` (`scarletwoman.md`#1) | Minion-only heirs; when the SW qualifies, no choice is offered — the app states the outcome |
| **T6** | N2 · Immediately after the star pass | `NightScreen.kt:467,520`; `GameActions.kt:88-94` | — | To show the new Imp their token; that they do **not** act tonight | **P0 ×2.** `starPass` keeps the heir's old reminders and note, places no `imp:"Dead"`, shows nothing, and marks no dormancy (`imp.md`#4,#6). And because the dead original and the live heir now both have `characterId == "imp"`, `QuickResolutions` takes `step.playerIds.firstOrNull()` and gates on `holder.alive` — **if the dead Imp sits earlier in the circle the kill panel disappears for the rest of the game** (`imp.md`#3) | Resolve to a single acting Demon seat; clear the heir's stale state; auto-open the YOU ARE card; mark "does not act tonight" |
| **T7** | D2 · Execute the Imp with a Scarlet Woman alive | `DayScreen.kt:111-114` → `WinCheck.kt:70-86` → `GameExtras` WinAdvisoryDialog | 2 | That the game is **not** over | **P0** Zero death notes on the Day-tab path (`scarletwoman.md`#2); then the win advisory returns `goodWins = true` with the SW demoted to a small caution that fires even when she is dead or fewer than five live (`scarletwoman.md`#8, `imp.md`#8) | The death funnel promotes her, states it, and suppresses the advisory |
| **T8** | N3 · The promoted Scarlet Woman | `NightOrder.kt:46-48,142-148`; `characters.json` scarletwoman | — | To tell her tonight; to place the `Demon` token | **P0** No state represents "she is the Demon and must be told tonight". Change her character and the `scarletwoman` row — with its YOU ARE prompt — vanishes; don't, and the `imp` row has no living holder so there is no kill panel (`scarletwoman.md`#3). The `Demon` token is placed by no code (`scarletwoman.md`#6) | A promotion event that swaps the character, places the token, inserts a one-off "tell the new Demon" row, and suppresses the SW row otherwise |

**Table B tally:** 16 P0 · 4 P1 · 1 data defect.

---

### Table C — Sects & Violets pass: Vortox · Cerenovus · Snake Charmer · Juggler · Savant

| # | Moment | Screen (file:line) | Taps | ST must remember / write elsewhere | Defect | Should be |
| --- | --- | --- | --- | --- | --- | --- |
| **V1** | N1 · Cerenovus picks a player **and a good character** | `NightScreen.kt:366-454` `GuideShowDialog` ×3 | ~12 + typing | **The character** — the app stores it nowhere | **P0** `PlacedReminder` is `(sourceId, label)` with no payload (`GameState.kt:6-11`) and the picker's selection is local state thrown away at dismiss (`NightScreen.kt:374-377`). At 3 p.m. tomorrow the ST cannot ask the app what the mad player is supposed to be claiming (`cerenovus.md`#1). Three cards = three dialog round-trips, and "Mad as…" makes you **search again for the character you just picked** (`cerenovus.md`#5); the "Madness demand" card is the literal placeholder `YOU ARE MAD THAT YOU ARE…` (`cerenovus.md`#6); the picker lists Demons and Minions (`cerenovus.md`#7) | One flow: player → good character → three cards in order, prefilled; a `Mad(as: characterId)` effect on the target |
| **V2** | N1 · Snake Charmer points at the Fang Gu | `NightScreen.kt:471-482` → `GameActions.snakeCharmerSwap` `:64-72` | 2 | Whether the charmer is droisoned; that alignments **swap**, not reset | **P0 ×3.** The panel asks "Charm hit the Demon? Pick the Demon" — the ST answers a question the app can answer, and a mis-tap swaps them with the Pit-Hag (`snakecharmer.md`#4). No `isImpaired` check anywhere in the branch, unlike `DemonKillPanel` at `:548` (`snakecharmer.md`#2). And `GameActions.kt:66-67` sets `alignmentFlipped = false` on **both** seats — a reset, not a swap, which turns an evil Snake Charmer good and corrupts Empath/Chef/Oracle/Investigator info and `WinCheck` (`snakecharmer.md`#1) | "Who did they point at?" → the engine answers "nothing happens" or performs the swap, preserving alignments and migrating character-bound tokens |
| **V3** | D1 · Juggler publicly guesses 5 characters | **nowhere**, then Grimoire ×5 | **20** (Grimoire → seat → Add reminder → Juggler → Correct, ×5) | All five (player, character) pairs, verbatim, while the Juggler is still talking | **P0 ×2.** No day input exists (`juggler.md`#2). And the tray can hold only **one** `Correct` token: `characters.json` declares one label, so `availableCopies == 1` and `NightScreen.kt:319-339` falls through to `placeExclusiveReminder`, which strips it from every other seat first (`GameActions.kt:194-201`, `juggler.md`#1). The 20-tap seat-sheet route is the only way to stack five | Guess entry on the Day tab (5 rows of player + character); the engine scores it, including Recluse/Spy/Drunk registration choices it already knows about |
| **V4** | D1 · Savant visits the ST | **nowhere** | 0 | Two sentences per day per Savant, each tagged true/false, for up to five days — plus not contradicting yourself later | **P0** No structure, no UI, no log, no "visited today" marker, no impairment prompt ("two true **or** two false"), no consistency check (`savant.md`#1-#6). The app computes exactly this class of fact for ~30 characters in `InfoCalc` and offers the Savant none of it (`savant.md`#3) | A Savant composer that proposes true facts from `InfoCalc`, records the pair with its verdict, and flags repeats/contradictions on later days |
| **V5** | D2 · The day ends with no execution, Vortox alive | `GameShell.kt:126-168`, `:608-612`; `WinCheck.kt` | 1 | That evil has already won | **P0 ×2.** No Vortox branch anywhere. Tapping "Dusk" on an execution-free day starts night 3 in silence; worse, the dusk guard's **"No execution"** button — the one that literally triggers the win condition — says nothing (`vortox.md`#1). And the app cannot represent "an execution happened but nobody died" (DA/Fool/Sailor/Tea Lady/Mayor), which is exactly the distinction the rule turns on (`vortox.md`#2) | A `day.executionOccurred` flag set by the death funnel even when the death is prevented; a dusk block: "No one was executed — **evil wins**" |
| **V6** | Every Townsfolk info step, all game | `InfoCalc.kt:161-164`, `NightScreen.kt:884-930` | 1–2 | The false answer, invented on the spot, for every non-numeric info shape | **P1 ×2.** The caveat "VORTOX in play — Townsfolk info must be FALSE" is computed, but the false-info helper only exists for numeric and YES/NO results. Washerwoman, Librarian, Investigator, Undertaker, Dreamer, Ravenkeeper, Grandmother, Shugenja, Knight, Steward, Noble, Sage, King, Balloonist, Bounty Hunter get the red warning and **no help at all** (`vortox.md`#3). Meanwhile the true answer's "Show N full-screen" chip sits one mis-tap away (`vortox.md`#8). There is also **no standing "VORTOX IS IN PLAY" indicator** (`vortox.md`#5) | Per-shape false-answer generation, and suppression of the true-answer chip while a Vortox lives |
| **V7** | N2 · Juggler's number | `InfoCalc.kt:29-36` | 1 | The count | **P1 ×2.** `juggler` is not in `InfoCalc.supports`, so nothing is computed even with tokens on the seat (`juggler.md`#3); and the step reappears every night thereafter, blocking the dawn guard each time (`juggler.md`#4) | Compute from the recorded guesses; show the step on exactly one night |
| **V8** | D2 · Enforcing madness | `StatusEffects.kt:162-164`, `DayScreen.kt:54-278` | 0 | Who is mad, as what, and whether they tried | **P0/P1 ×3.** No day briefing, no banner, no "execute for broken madness" action, and no accounting that such an execution spends the day's execution (`cerenovus.md`#2). The only surface is a nomination warning that fires **only when the mad player is the nominator** (`cerenovus.md`#3) and matches any `Mad` label — including the Harpy's `("harpy","Mad")` and the generic tray token (`SeatSheet.kt:502`, `GameActions.kt:239`, `cerenovus.md`#4) | Day briefing line "Ivan is mad that he is the **Empath**"; an execute-for-madness action; source-scoped matching |

**Table C tally:** 12 P0 · 7 P1.

---

### Table D — Courtier (13th named character; same defects, night-order-independent)

| # | Moment | Screen (file:line) | Taps | ST must remember | Defect | Should be |
| --- | --- | --- | --- | --- | --- | --- |
| **D1** | N1 · Courtier points at a **character** on the sheet | "Sheet" chip `NightScreen.kt:254-262`; tray is seat-based `:283-295` | 3–5 | Whether that character is in play, and in **which seat** | **P1** Every night affordance in the app is seat-based; the Courtier is the one character that chooses a *character*. The app already knows the answer and asks the ST to scan the grimoire by eye (`courtier.md`#3). Nothing records what was pointed at (`courtier.md`#4), and "spent with no effect" (not-in-play target) is inexpressible (`courtier.md`#5) | A character picker over the script that answers "**Po** — not in play. Ability spent, nothing happens" or "**Pukka** — that's Kai. [Make Kai drunk for 3 nights]" |
| **D2** | N2–N5 · The 3-night countdown | `GameActions.kt:218-242,258-263` | 4/night by hand | To decrement `Drunk 3 → 2 → 1` and remove it | **P0** Neither expiry table contains any Courtier token and `advancePhase` has no decrement logic. A player made drunk on night 2 **stays drunk for the whole game** and `isImpaired` keeps returning true — the same class of failure as the reported Devil's Advocate bug (`courtier.md`#1) | Typed effects with durations: `Drunk(source=courtier, remaining=3 nights & days)`, decremented by the engine, expiring by itself |
| **D3** | N2+ · The Courtier's step after "Mark spent" | `NightOrder.kt:147-148`, `NightScreen.kt:263-279` | 1/night | That she must never be woken again | **P0** The step still renders the full conditional sentence including "The Courtier either shows a 'no' head signal, or points to a character", inviting the ST to wake a spent Courtier (`courtier.md`#2) | Auto-skip: "Courtier's ability is spent" |

---

## Proposed behaviour (spec) — the systemic changes, in priority order

The user asked us to *"pick up on the idea and generalize"*. Below are the
general principles, each stated as a rule the codebase should obey, with the
concrete change and the rows it removes. They are ordered by friction removed
per unit of work.

### §1 — One death pipeline. *Every protection and every trigger is evaluated at the single moment of death, on every path.*

There are five places that end a life and **only one** consults
`StatusEffects.deathNotes`:

| Path | File | Consults deathNotes? |
| --- | --- | --- |
| Day tab block banner "Execute" | `DayScreen.kt:111-114` | **no** |
| Nomination row "Execute" / "Exile" | `DayScreen.kt:350-357` | **no** |
| Dusk guard "Execute & begin night" | `GameShell.kt:598-605` | **no** |
| Demon kill panel "X dies" | `NightScreen.kt:624-637` | shows notes, **kills anyway** |
| Seat sheet "Died at night / Executed / Other death" | `SeatSheet.kt:266-307` | yes, via a substring grep |

Replace all five with

```
GameActions.attemptDeath(state, targetId, cause, sourceCharacterId?)
  -> DeathOutcome.Killed(state, triggers)          // on-death triggers already applied
   | DeathOutcome.Prevented(state, by: EffectRef)  // recorded, not silent
   | DeathOutcome.AlreadyDead
```

`Prevented` must still be **recorded** (a `DeathAttempt` in state), because
"an execution happened but nobody died" is load-bearing for the Vortox
(`vortox.md`#2), the Undertaker, the Mayor and the Leviathan.

Removes/fixes: F21, F27c, F42, F43, F51, T2, T5, T7, V5 — and every protection
defect in `fool.md`#1-2, `sailor.md`#1-3, `tealady.md`#2,#5, `devilsadvocate.md`#1,
`scarletwoman.md`#1-2, `grandmother.md`#4, `imp.md`#9.

### §2 — The storyteller inputs a *choice*; the engine computes the *consequence*.

Today exactly four steps have a resolver (`NightScreen.kt:462-525`:
`snakecharmer`, `fanggu`, `professor`, and the catch-all "any Demon"), which is
why a Pukka is offered a kill, a Godfather is offered nothing, and a Lunatic is
offered nothing. Do not add a fifth `when` branch — build a declarative night
action, exactly in the shape the BRIEF asks each character spec to be written:

```
NightAction(
  when:      FIRST | OTHER | BOTH,
  wakes:     (state, holder) -> Wakes | Skip(reason) | StorytellerOnly(reason),
  targets:   TargetSpec(count, alive?, notSelf?, differentFromLastNight?, team?, characterPicker?),
  immediate: List<Effect>,        // tokens, poison/drunk/protect/mad, kills, identity changes
  deferred:  List<Deferred>,      // at dawn / day start / next night / on death / on execution
  expiry:    ExpirySpec,          // dawn | dusk | nights(n) | onCondition | never
  info:      InfoSpec?,           // true answer + false alternative + misregistration
  visibility:List<ShowSpec>       // what the Demon / Minions / Lunatic are shown
)
```

The 130+ character specs already written under `docs/audit/characters/` are
authored in this vocabulary; this is the engine that consumes them.

Removes: F9, F11, F13, F14, F24, F25, F26, F27a, F28, F29, F41, F43, T1, T3,
T5, V1, V2, D1.

### §3 — Nothing that happened at night may be forgotten by dawn: record **choices**, not just tokens.

`GameState` (`GameState.kt:93-115`) has no night-choice history — only
`nightStepsDone: Set<String>`, cleared at every phase change
(`GameActions.kt:259,262`). Worse, the expiry tables actively delete the
evidence *before* it is needed: `devilsadvocate:Survives execution` at dusk
(`GameActions.kt:236,261`), `exorcist:Chosen` at dawn (`:221,260`).

Add `nightChoices: List<NightChoice(cycle, sourceId, actorId, targetIds,
characterId?, outcome)>`. Token expiry then becomes a *display* rule and stops
doubling as the app's memory.

Unlocks: "different to last night" (Devil's Advocate, Exorcist, Balloonist),
"last night's pick" (Sailor, Butler, Cerenovus), the Po's 3-attack charge,
once-per-game spent-ness, the Mathematician, and a real game log.
Removes: F22, F24, F26, F39, F41, F52.

### §4 — A token is a **projection** of a rule, not the rule itself. Effects must be typed and dated.

Today: `isImpaired` greps reminder labels for the substrings `"poison"` and
`"drunk"` (`StatusEffects.kt:36-46`); protection is a `List<String>` of English
prose that `SeatSheet.kt:256-265` greps for `"can't die"`, `"Safe"`, `"don't"`,
`"Fool"`; a countdown token cannot count; a character that needs two identical
tokens (Tea Lady) or five (Juggler) cannot have them, because
`placeExclusiveReminder` (`GameActions.kt:194-201`) moves the single declared
label.

```
Effect(kind = POISON|DRUNK|PROTECT|IMMUNE|MAD|MARK|SPENT,
       source = characterId, target = playerId,
       from = Cycle, until = Dawn|Dusk|Nights(n)|Condition|Never,
       payload = characterId? | Int?)
```

Reminder tokens are then *rendered from* effects, and `characters.json`
reminder counts become presentation data, not a hard cap.
Removes: F50, F43c, T1, V3, D2 — and `fool.md`#7, `sailor.md`#7,
`tealady.md`#3,#7, `juggler.md`#1,#10, `cerenovus.md`#4.

### §5 — Dawn is a **report**, not a word.

`NightOrder.kt:59` is a constant string: *"Wait a few seconds. Everyone opens
their eyes. Announce who died."* The app knows who died, of what, who is alive
again, and who silently survived. Make DAWN a computed briefing with three
sections: **announce** (deaths, in order; resurrections), **do not announce**
(silent saves — Fool, Sailor, Tea Lady, Monk), **you must still do** (place
tokens, re-run a first night).

The user's exact words: *"When Professor brings someone back it should remind in
the morning and rerun the 1st night for that."* Both halves are this principle
plus §7.
Removes: F17, F33, F48 — and `pukka.md`#7, `professor.md`#2, `grandmother.md`#8,
`po.md`#6, `imp.md`#7, `gossip.md`#7.

### §6 — Day start is a briefing too. Standing facts are computed, never remembered.

There is no day surface for state: `GameShell.requestPhaseAdvance` only switches
tabs (`GameShell.kt:162-168`) and `DayScreen.kt:85-124` shows an alive count and
a threshold. Every single character audit in this scope independently asked for
the same thing. A "Today" card computed from state:

- who is poisoned/drunk **and until when** ("Dev is poisoned — dies at the Pukka's next wake");
- who survives execution today; who cannot die and why;
- who is mad, **as what**, and whether they have tried;
- whose once-per-game is spent; whose is still live (Slayer, Professor, Courtier);
- clocks: Vortox "someone must be executed today", Leviathan day count, Mayor;
- outstanding public actions: Gossip statement, Juggler guesses, Savant visit, Slayer shot.

Removes: F19, F34, F49, V8 — and the `#day briefing` defect in eleven audits.

### §7 — The night sheet is a function of **tonight's state**, not of the script — and it must accept insertions.

`NightOrder.build` (`NightOrder.kt:142-178`) emits a row for every in-play
character on the global order list, with no per-night condition; `GameShell.kt:147-161`
then refuses dawn until every row is ticked. Two changes:

1. every row gets `wakes(state) -> Wakes | Skip(reason) | StorytellerOnly(reason)`,
   and skipped rows are auto-marked with their reason rather than counted as
   unfinished (dead holders, spent once-per-games, an unarmed Godfather, a
   Juggler past their night, an Exorcised Demon, a Zombuul on a death night);
2. the sheet accepts **inserted** rows for events that happened *during* the
   night: a resurrected or character-changed player's first-night step, a
   Scarlet Woman promotion, a Snake Charmer hand-over, a new Imp's YOU ARE.

Removes: F18, F28, F31, F38, F40, F45, F46, T6, T8, V7, D3.

### §8 — The day must be an **input surface**, not a black hole: one public record.

The user asked for this by name — *"make it easy to write down all the gossips
even if Gossip isn't in play"*. One structure serves eight characters:

```
PublicStatement(day, speakerId, kind, text, subjects: List<PlayerId|CharacterId>, verdict: TRUE|FALSE|UNJUDGED)
kind = GOSSIP | JUGGLE | SLAYER_SHOT | SAVANT_VISIT | ARTIST_Q | FISHERMAN_Q |
       MUTANT_BREACH | NIGHTWATCHMAN | CLAIM
```

Entered in two taps from the Day tab; replayed **verbatim at the night step
that consumes it**; visible in the log. `CLAIM` is the general case the user
asked for: a running ledger of who claimed what, which the ST needs for
Cerenovus, Pit-Hag, Vortox and Mutant rulings whether or not any of those
characters is in play.
Removes: F35, F44, T3, V3, V4 — and `gossip.md`#1,#5,#6, `juggler.md`#2,
`savant.md`#1,#5, `slayer.md`#1,#8, `virgin.md`#8.

### §9 — Identity is **layered**: true character, believed character, and what each audience has been told.

`shownCharacterId` (`GameState.kt:23`) covers the token and nothing else. The
Lunatic needs four more things, all of which the user listed:

| User's words | Change |
| --- | --- |
| "should have its own bluffs" | `lunaticBluffIds` alongside `demonBluffIds`, allowed to include in-play characters (`BluffsSheet.kt:40-45` currently forbids it) |
| "should fit the correct demon it thinks it is" | the Lunatic's night action is generated from `shownCharacterId`: Po ⇒ 1-or-none-then-3, Shabaloth ⇒ 2, Pukka ⇒ poison and **wakes night 1**, Zombuul ⇒ only on no-death nights, Imp ⇒ self is legal |
| "should show the real demon who they chose" | a first-class hand-off: the Lunatic's Attack marks become a show-card sequence on the **Demon's** row, with a "told" tick — not a sentence appended to `detail` at `NightOrder.kt:163-167` |
| (implied) fake Minions | N arbitrary players recorded as `lunatic:Fake minion` tokens, N = Minion count, computed |

Removes: F5, F9, F25, F40 — and `lunatic.md`#1-#6.

### §10 — Information the grimoire can compute must never be asked of the storyteller; and where it must be false, the app must supply the lie.

`InfoCalc.supports` covers 26 ids (`InfoCalc.kt:29-36`). Missing and trivially
computable: **Godfather** (which Outsiders are in play), **Juggler** (count the
guesses), **Exorcist** ("is this seat the Demon?"), **Courtier** ("is that
character in play, and in which seat?"), **Savant** (propose true facts),
**Tea Lady** (is the protection on?), **Chambermaid** (rewrite: count what the
sheet actually did tonight, `InfoCalc.kt:465-482`).

And the false-info helper must exist for **every** info shape, not only numeric
and YES/NO (`NightScreen.kt:884-930`): character-plus-pair, direction,
"point to N players such that P", character reveal. While a Vortox lives, the
true answer's show chip should be withheld.
Removes: F12, F15, F16, F32, F47, V6, V7, D1.

### §11 — Every rule the app *states* must be true. Guide prose is code.

Data-authored sentences currently mis-state the rules and will make a
storyteller rule incorrectly at the table:

| Where | Says | Should say |
| --- | --- | --- |
| `night_guide.json` butler.first | "This still applies even if the Butler is drunk or poisoned" | A droisoned Butler may vote freely (`butler.md`#3) |
| `night_guide.json` vortox.other | "no one dies, **but information is still false**" | A droisoned Vortox's ability does not function; flagged as very likely wrong (`vortox.md`#6) |
| `night_and_jinxes.json:230-231` | "If Riot kills the Grandchild, the Grandmother dies too" | "If Riot is in play and the Grandchild dies by execution, **evil wins**" — a win condition (`grandmother.md`#1) |
| `NightOrder.kt:150-154` on a Pukka | "the Demon does not act tonight" | "…places no new poison — but last night's victim still dies" (`pukka.md`#2) |
| `night_guide.json` exorcist.other | "THIS PLAYER STOPPED YOU TONIGHT" | "THIS CHARACTER SELECTED YOU" + Exorcist token (`exorcist.md`#7) |

Every guide string should be reviewed against the wiki once, and thereafter
treated as reviewable content, not copy.

### §12 — Phone ergonomics: one hand, in the dark, at a table of twelve.

Three measured costs from Table A:

1. **The 12-seat horizontal `LazyRow`** (`NightScreen.kt:314-352`) must be
   scrolled for every single token placement — 3 times in F25 alone. Use the
   grimoire circle itself as the target surface: pick a token, tap a seat.
2. **`token:"pick"` show cards** open a search box for a character the app
   already knows (`NightScreen.kt:392-435`) — F9, F12, F15, V1, and
   `snakecharmer.md`#6, `scarletwoman.md`#10, `lunatic.md`#11. Default the token
   to the known answer; keep search as an override.
3. **Grimoire → seat → Add reminder → source → label** (5 taps) is the *only*
   way to place a second copy of a token, and the only place several characters
   can be resolved at all. It should never be the primary path for a rule the
   app understands.

---

### Priority summary

| Rank | Change | Principle | Rows removed | Est. blast radius |
| --- | --- | --- | --- | --- |
| 1 | Single `attemptDeath` funnel, recording prevented deaths | §1 | 9 | ~25 audits |
| 2 | Declarative `NightAction` replacing the four hard-coded resolvers | §2 | 18 | all 130+ |
| 3 | `nightChoices` history in `GameState` | §3 | 6 | ~20 |
| 4 | Typed `Effect` with durations, tokens rendered from effects | §4 | 5 | ~30 |
| 5 | Dawn report + Day briefing (two computed cards) | §5, §6 | 7 | ~40 |
| 6 | `PublicStatement` ledger on the Day tab | §8 | 5 | 8 characters, user-requested |
| 7 | Conditional + insertable night sheet | §7 | 11 | ~25 |
| 8 | Lunatic identity kit (own bluffs, believed-Demon action, hand-off) | §9 | 4 | user-requested |
| 9 | `InfoCalc` coverage + false info for every shape | §10 | 8 | ~35 |
| 10 | Guide/jinx prose audit; ergonomics pass | §11, §12 | — | app-wide |

---

## Tests to add

Engine-level Given/When/Then cases that fail today. All are pure `GameActions` /
`StatusEffects` / `InfoCalc` / `NightOrder` tests, no UI.

**Death pipeline (§1)**
1. *Given* a player with `devilsadvocate:Survives execution`, *when*
   `attemptDeath(cause=EXECUTION)`, *then* the outcome is `Prevented`, the
   player is alive, and `state` records an execution attempt for the day.
2. *Given* a sober Sailor, *when* `attemptDeath(cause=DEMON)`, *then*
   `Prevented`. *Given* the same Sailor carrying `pukka:Poisoned`, *then*
   `Killed` — and `deathNotes` must not contain "The Sailor can't die".
3. *Given* a Fool with no `No ability` token, *when* `attemptDeath(EXECUTION)`,
   *then* `Prevented` **and** `fool:"No ability"` is now on the seat; a second
   attempt returns `Killed`.
4. *Given* a poisoned Fool, *when* `attemptDeath`, *then* `Killed`.
5. *Given* a Tea Lady at seat 7 whose seat-6 neighbour is **dead** and whose
   nearest alive neighbours (5 and 8) are both good, *then* seats 5 and 8 are
   protected and seat 6 is not.
6. *Given* an Imp killed by execution with a Scarlet Woman alive and 4 real
   players + 2 Travellers alive, *then* the SW does **not** become the Demon
   (currently she does, `StatusEffects.kt:105`).

**Pukka / deferred consequences (§2, §4)**
7. *Given* night 1, *when* the Pukka acts, *then* no kill is offered and
   `pukka:Poisoned` is placed; `state` records a pending death for the token
   holder.
8. *Given* night 2 with `pukka:Poisoned` on Ben, *when* the Pukka poisons Dev,
   *then* Ben dies **with** `abilityImpairedAtDeath == true`, Ben's poison is
   cleared, and Dev is poisoned — in that order, from one action.
9. *Given* the Pukka carries `exorcist:Chosen`, *when* the night resolves,
   *then* no new poison is placed **and** the previously poisoned player still
   dies.
10. *Given* the Pukka's target is protected, *when* the death is prevented,
    *then* the target's `pukka:Poisoned` token is removed (they become healthy).

**Memory of choices (§3)**
11. *Given* the Devil's Advocate chose Lena on night 1, *when* night 2's action
    is built, *then* Lena is present in `nightChoices` and the target spec
    excludes her.
12. Same for the Exorcist across dawn (the token is swept; the choice is not).
13. *Given* a Lunatic-as-Po that chose no-one on night 2, *then* night 3's
    Lunatic action requires exactly 3 targets.

**Godfather (§2, §5)**
14. *Given* an Outsider dies **at night**, *then* no Godfather arming occurs
    and `deathNotes` does not mention the Godfather.
15. *Given* an Outsider dies **by day-time execution**, *then*
    `godfather:"Died today"` is placed, the Godfather's next-night step wakes,
    and the token is swept at the following dawn.
16. *Given* two Outsiders die on the same day, *then* the Godfather still gets
    exactly one kill.

**Professor / resurrection (§5, §7)**
17. *Given* a dead **Minion**, *when* the Professor targets them, *then* the
    ability is spent and the player stays dead.
18. *Given* a poisoned Professor, *then* the ability is spent and no
    resurrection occurs.
19. *Given* the Professor resurrects a Grandmother, *then* (a) `professor:Alive`
    is placed, (b) the dawn report contains "alive again", (c) tonight's sheet
    contains an inserted `grandmother · firstNight` step, and (d) any
    `No ability` token on the resurrected seat is cleared.

**Chambermaid (§10)**
20. *Given* night 2 with a Gossip and a Grandmother alive, *when* the
    Chambermaid picks both, *then* the answer is **0**, not 2.
21. *Given* a Drunk shown the Empath, *then* they **count** as waking; *given* a
    Marionette on night 1, *then* they do **not**.
22. *Given* an Exorcist-silenced Demon, *then* the Demon does **not** count.

**Day surfaces (§6, §8)**
23. *Given* a Gossip statement recorded on day 2 with `verdict=TRUE` and a
    Gossip who is poisoned at the moment of the statement, *then* the night-3
    Gossip step reports "no death" and offers no victim picker.
24. *Given* a Juggler with 5 recorded guesses, 2 correct, *then*
    `InfoCalc.compute("juggler")` returns 2 and the step appears on exactly one
    night.
25. *Given* a Vortox alive and a day that ends with no execution attempt,
    *then* `WinCheck` returns an evil-wins advisory at dusk; *given* an
    execution that was **prevented**, *then* it still counts as an execution.

**Identity (§9)**
26. *Given* a Lunatic with `shownCharacterId == "pukka"`, *then* the first-night
    sheet contains a Lunatic action with a poison-shaped target spec.
27. *Given* a Lunatic and a Demon, *then* `lunaticBluffIds` may contain in-play
    characters and `demonBluffIds` may not, and the two lists are independent.
28. *Given* the Lunatic is dead, *then* the Demon's step carries no
    "wake them first" annotation.

**Snake Charmer / alignment (§2)**
29. *Given* an evil Snake Charmer and a good Demon (Pit-Hag scenario), *when*
    the swap resolves, *then* both keep their alignments (currently both are
    reset to default, `GameActions.kt:66-67`).
30. *Given* a droisoned Snake Charmer pointing at the Demon, *then* nothing
    happens.
