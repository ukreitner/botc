# Digest — Experimental Townsfolk C+D (14 characters)

huntsman · king · knight · lycanthrope · magician · nightwatchman · noble · pixie ·
poppygrower · preacher · princess · shugenja · steward · villageidiot
Totals: **P0 = 17, P1 = 61.** Types below are from `mechanics/night-engine.md`,
`mechanics/status-model.md`, `mechanics/day-engine.md`, `mechanics/records-and-memory.md`.

## Group notes

1. **New `EffectKind.DEMON_CANNOT_KILL`** (on the Demon seat, `until = DAWN`) + `killOutcome`
   rule **1.5** — `lycanthrope` and `princess` both block the Demon's kill, and the wiki's own
   example is a **deferred Pukka** kill, so a "disable the dies button" fix is provably wrong.
2. **Kill causes fall out for free**: `KillCause(GOOD_ABILITY,"lycanthrope")` is correctly
   unblocked by Monk/Soldier and blocked by Sailor/Innkeeper/Tea Lady/Fool — but `SeatSheet`'s
   one "Died at night" → `DeathCause.DEMON` button must be split (it breaks Choirboy + Sage).
3. **`Status.registersEvil()` is missing.** `REGISTERS_AS` has no resolver; the Lycanthrope's
   `Faux Paw` changes `noble`, `steward`, `shugenja`, `villageidiot` in this group alone.
4. **`No ability` means three different things** (Preacher suppression / once-per-game spent /
   Professor). Key on `sourceId`, compare case-insensitively. Preacher suppression is exactly
   `Effect(NO_ABILITY, sourcePlayerId = preacher, endsWithSource = true)` — §2 does the rest.
5. **Four new `SetupTask` rows**: `knight.know`(2), `noble.know`(3), `steward.know`,
   `lycanthrope.fauxpaw`. `pixie.madAs` and `villageidiot.drunk` are already in that table.
6. **"You start knowing" never re-runs** — `knight`/`noble`/`steward`/`shugenja`/`pixie` are
   `firstNight`-only, so a mid-game holder gets no step ever. Consumers of `WakeStyle.FIRST_NIGHT`.
7. **`GameState.abilityHolders(characterId)`** — `pixie:Has Ability` must wake the Pixie on the
   *gained* character's row from their own seat. Shared with Boffin/Alchemist/Cannibal/Philosopher.
8. **`MINION_INFO`/`DEMON_INFO` are contested twice here**: `magician` rewrites their content,
   `poppygrower` suppresses them for a `DEMON_BLUFFS_ONLY` step. Neither marker has a
   `night_guide.json` entry. One shared builder must serve both plus the Poppy Grower reveal.
9. **conflict:** these files invented `pixieMadCharacterId`/`huntsmanGuess`/`princessFirstDay`/
   `infoGiven`; translated here to `LedgerEntry` + `PlacedReminder.characterId` +
   `Player.standingSince`. `DayEntry` vs `LedgerEntry` is still unresolved — lead must settle it.
10. **`StepKey(id, playerId)` is mandatory, not an optimisation**: `villageidiot`'s single
    picker + `playerIds.firstOrNull()` gives the *wrong* holder's impairment caveat, so the app
    confidently hands true info to the drunk Village Idiot every night of the game.

---

## huntsman — Huntsman · Experimental Townsfolk · P0:0 P1:6

today: no resolver at all — no picker, no spend, no transformation, no impairment banner; the
step keeps appearing after the ability is spent and the Dawn guard blocks on it.
data:
  - characters.json: ok (`setup: true`, `reminders: ["No Ability"]`, text matches wiki).
  - night_and_jinxes.json: ok (first 43 / other 65, Damsel at 44 / 66; marionette jinx present).
  - night_guide.json:1036-1059: add the wiki inverse — "a drunk or poisoned **Damsel** still
    becomes the Townsfolk; only an impaired **Huntsman** stops it"; add "the Huntsman learns
    nothing about whether the guess was right".
setup: none (bag side already correct — `Setup.COMPANIONS["huntsman"]="damsel"`, +1 Outsider,
  `validateBag` requires the Damsel).
identity: plain. A Marionette shown `huntsman` runs the flow as theatre — spend nothing, change
  nothing (`Gates.hasAbility` -> `Skip`), badge "Marionette believing they are the Huntsman".
night.first / night.other (identical):
  gate: `Gates.aliveHolder` + `Gates.notSpent("huntsman", "No Ability")`; when spent ->
    `StepGate.Skip("used on night N — guessed <name>")`, auto-ticked, `[Undo spend]`.
  action: `Sequence`, stage 1 `ChoosePlayers(min=1, max=1, constraints=[ALIVE, SELF_ALLOWED],
    sort = SEAT_ORDER, allowNone = true, noneLabel = "Declined — no guess")`. Never highlight
    the Damsel's seat. Stage 2 fires only if stage-1 target is the Damsel **and**
    `Status.hasAbility(huntsman)`: `ChooseCharacter(pool = NOT_IN_PLAY, filter TOWNSFOLK,
    allowNone = false)` — alphabetical, **no in-play section** (today's dialog sorts in-play
    first, exactly backwards).
  effects: any guess -> `MarkSpent("huntsman")` (writes `huntsman:No Ability`, exclusive,
    `Expiry.NEVER`) + `RecordChoice()`. Stage 2 -> `ShowCardTo(TARGET, "YOU ARE")`,
    `BecomeCharacter(TARGET, <picked>, evil = false, clearOldTokens = true)` — `clearOldTokens`
    must drop every `sourceId == "damsel"` effect/token (`damsel:Guess Used`) and offer the
    Spy/Widow-jinx poison as a tick-box rather than guessing.
  deferred: none.
  info: the Huntsman learns nothing — say so in one line. `ShowInfo` not needed; add
    `preacher`-style impairment caveats via the cross-cutting `Status.impairment` hoist.
  show: `YOU ARE` + the chosen Townsfolk token (existing `ShowCard.CharacterCard`).
  visibility: nothing to Demon/Minions. Note that the Minions were told on night 1 that a
    Damsel is in play and are **not** told she changed.
day: `DayBriefing.Slot.NOMINATION` / day tools — a "Minion publicly guessed the Damsel"
  recorder (shared with `damsel`). After the transform it must answer **"no effect — that
  player is no longer the Damsel"**, and `checkNomination` must stop emitting a Damsel trigger.
death: none.
ledger: `LedgerEntry(kind = CHOICE, sourceId = "huntsman", targetIds = [guess], impaired = …)`
  and `kind = TOLD` for the Damsel's new character. Powers the spent-row text and undo.
tests:
  - sober Huntsman guesses the Damsel -> Damsel's `characterId` is the picked not-in-play
    Townsfolk, `huntsman:No Ability` placed, **no** `sourceId == "damsel"` token remains.
  - Huntsman holding `No Ability` -> no `huntsman` step on cycle 2 (fails today).
  - poisoned Huntsman guesses the Damsel -> Damsel unchanged, ability still spent.
  - sober Huntsman + **poisoned** Damsel -> the change still happens (wiki clarification).
  - Marionette shown `huntsman` runs the resolver -> state unchanged.
open: does the transformed Damsel receive her new character's first-night info? Wiki silent —
  raise a `Prompt(at = NOW, kind = RUN_FIRST_NIGHT)` and record the ST's answer; never decide
  silently. Setup abilities of the chosen Townsfolk do **not** apply (warn).

## king — King · Experimental Townsfolk · P0:0 P1:6

today: night 1 prints `"Dead (0) don't outnumber living (12) — the King doesn't wake"` over a
step that is actually *the Demon learning the King*; the wake condition is only visible when
the row is expanded; Leviathan/Riot jinxes absent; Choirboy coupling is one prose line.
data:
  - characters.json: ok (text matches; `setup: false`; no reminders).
  - night_and_jinxes.json: **add** `king`+`leviathan`: "If the Leviathan is in play, and at
    least 1 player is dead, the King learns an alive character each night." and `king`+`riot`
    with the same wording. Order ok (first 19, other 85).
  - night_guide.json:1062: delete/soften "Skip this step if a Poppy Grower is in play and
    alive" — unsupported by both the King and Poppy Grower pages (the Poppy Grower removes
    *Minion Info and Demon Info* only). :1076: drop the invented `THIS CHARACTER IS ALIVE`
    token text — the ST just shows the character token. Add "the King may learn the same
    character on different nights".
setup: none.
identity: plain.
night.first:
  gate: `Fire` whenever a King seat exists — **this step is about the Demon**. Suppress the
    `InfoCalc` headline entirely for cycle 1 (P1 #1).
  action: `ShowInfo(targetsNeeded = 0)`.
  info/show: `THIS PLAYER IS` + King token, then point at the King's seat; one-tap card that
    also names the seat.
  visibility: the Demon only; with multiple Demon seats (Legion/Riot/Kazali) show each.
  deferred: **a King created mid-game** -> `Prompt(at = TONIGHT, characterId = "king",
    kind = INFO, title = "The Demon learns the new King")`, inserted by `NightPlan` at the
    King's order position (wiki: "the Demon learns who the King is that night").
night.other:
  gate: `Gates.aliveHolder` + new `Gates.deadAtLeastAlive()` = `deadCount >= aliveCount`
    **OR** (`leviathan`/`riot` in play && `deadCount >= 1`). Counting = **all seats including
    Travellers** (wiki silent) — the row must print the breakdown ("7 dead / 6 alive, incl.
    1 Traveller") rather than hide the arithmetic. Not-waking -> `Skip("1 dead vs 11 alive —
    the King needs dead >= alive")`, auto-ticked.
  action: `ShowInfo(targetsNeeded = 0)`; the **ST** picks which alive player's character is
    shown. Render every alive seat as `<name> — <character>` chip; sort characters not shown on
    a previous night first (read `Memory` for the shown history), then seat order.
  effects: none, no tokens.
  deferred: the first night the condition becomes true, add a `DuskBriefing.countdowns` /
    `DayBriefing` line: "From tonight the King wakes every night (dead >= alive)." — the app's
    equivalent of the physical night token.
  info: true answer = any alive player's character. Impaired (drunk/poisoned/Drunk/Marionette)
    or **Vortox** -> `falseAlternatives` = characters that are **not** alive (in-play
    deprioritised); today's Vortox caveat gives no chips at all. No misregistration applies
    (a character, not an alignment, is learned).
  show: `ShowCard.CharacterCard("", characterId)`.
death: **Choirboy trigger belongs here.** `DeathTrigger(characterId = "choirboy",
  matches = event.characterIdAtDeath == "king" && event.cause.kind == DEMON_ABILITY &&
  event.atNight && Status.hasAbility(choirboy), produce = Prompt(at = TONIGHT, kind = INFO,
  title = "Wake the Choirboy (Bo). Point to Ali — the Demon."))`. Must **not** fire on
  execution, Minion kills, or a failed attack. Gate `StatusEffects.kt:102`'s prose line on
  `cause.kind == DEMON_ABILITY` **and** a living unimpaired Choirboy, and name their seat.
  Also a `DawnReport.privateNotes` line: "Amy (King) was killed by the Demon — the Choirboy
  learned who the Demon is."
ledger: `kind = TOLD, sourceId = "king", shown = "<character>"` per night, so repeats can be
  avoided and day-time challenges answered.
tests:
  - 6 alive / 6 dead -> the King wakes (equality qualifies); 7 alive / 6 dead -> does not.
  - cycle 1 -> headline describes the Demon learning the King, not the dead/alive count (fails).
  - Leviathan + exactly 1 dead of 10 on night 4 -> the King wakes (fails today).
  - Demon kills the King on cycle 3 with a living Choirboy -> a Choirboy prompt naming the
    Demon's seat exists (fails); the same kill by an Assassin -> no prompt.
open: do Travellers count toward dead/alive? Wiki silent — show the breakdown and let the ST
  decide; store the choice on the game.

## knight — Knight · Experimental Townsfolk · P0:0 P1:4

today: nothing at setup; on night 1 `InfoCalc.knight` returns a static "Point to 2 players that
are NOT the Demon" plus "Demon: Ali" — it never reads the placed tokens, proposes no pair, and
nothing stops both `Know` tokens landing on the Demon.
data:
  - characters.json: ok (`reminders: ["Know","Know"]`; first-night only).
  - night_and_jinxes.json: ok (first 58, correctly absent from otherNight; no jinxes exist).
  - night_guide.json:1084-1089: add "the 2 players may be any characters other than the Demon,
    including Minions" and "under a Vortox, one of the two must be the Demon".
setup: `knight.know` · PICK_PLAYERS(2) · "Knight — mark 2 players who are not the Demon." ·
  candidates = all non-Demon seats (suggest a spread pair, avoid the Recluse) ·
  apply = `PlacedReminder("knight","Know")` ×2 (cap 2, not exclusive) ·
  satisfied/validation = exactly two `("knight","Know")`, neither on a `Team.DEMON` seat —
  **unless** a Vortox is in play, when at least one **must** be. Issue text:
  `"Knight: mark exactly 2 non-Demon players with Know"`.
identity: plain.
night.first:
  gate: `Gates.aliveHolder`. Also fires as a `variant = "first"` re-run on the cycle a seat
    first holds `knight` (mid-game Pit-Hag/Amnesiac/Huntsman-created Knight gets nothing today).
  action: `ShowInfo(targetsNeeded = 0)` — the ST marks seats, the player picks nothing.
  effects: two `knight:Know` tokens, `Expiry.NEVER`, `maxCopies = 2`. Must never enter the
    dawn/dusk tables.
  info: headline reads the tokens **back** — `Point to Bo and Cara — neither is the Demon.`
    Missing tokens -> `Pick 2 of: <chips for every non-Demon seat>` with one-tap placement and
    a suggested default. Detail: `Demon: Ali (excluded). Legal: everyone else, incl. Minions.`
    Impaired -> "any 2 players are legal, including the Demon", chip list expands to all seats.
    Vortox -> "the Knight's info must be FALSE: at least one of the two must be the Demon",
    suggested pair becomes (Demon, other). Misregistration: emit **only** the Recluse note
    ("may register as the Demon — marking them KNOW is a real, legal risk"); **suppress the
    Spy note entirely** (a Spy is always a legal target, so the generic line is noise).
  show: needs the shared `ShowCard.PlayerCard` (see `steward`) to point at seats on a phone.
  visibility: nothing to evil.
day: `LedgerEntry(kind = STATEMENT, sourceId = "knight")` — record the Knight's public claim and
  cross-check it against the placed tokens and the snapshot impairment.
ledger: snapshot the holder's impairment at the moment the info is given (mirror of
  `DeathEvent.impairedAtDeath`), stored on the `TOLD` entry.
tests:
  - Knight in play, no `Know` tokens -> `validateSetupState` reports a Knight issue (fails).
  - a `Know` token on the Imp -> validation reports "must not be on the Demon" (fails).
  - `Know` on Bo and Cara -> headline names Bo and Cara (fails — headline is static).
  - Vortox in play -> caveats say one of the two must be the Demon (fails).
  - Recluse + Spy in play -> caveats mention the Recluse and **not** the Spy (fails).
  - a seat becomes the Knight on cycle 3 -> a Knight step appears (fails).
  - full dawn+dusk -> both `Know` tokens survive.
open: none.

## lycanthrope — Lycanthrope · Experimental Townsfolk · P0:3 P1:6

today: no resolver, no impairment banner, no registration model, no setup prompt, and — the P0 —
**nothing blocks the Demon's kill**, tonight's or deferred. The ST must hold the block in their
head across twenty later steps.
data:
  - characters.json: ok (`reminders: ["Faux Paw","Dead"]`; text is the **current, narrower**
    wording — it blocks the Demon's kill only, not all deaths).
  - night_and_jinxes.json: ok (other-night 33, before every Demon; Gambler jinx present at
    :163-167). No page jinxes are missing.
  - night_guide.json:1090-1095: add the wiki's Pukka example verbatim (a Demon's **deferred**
    kill also fails), the "an alive player" constraint (the How-to-Run says "any player" —
    ability text binds), and "Soldier/Monk do **not** protect against the Lycanthrope".
setup: `lycanthrope.fauxpaw` · PICK_PLAYER · "Lycanthrope — pick the good player who registers
  as evil (Faux Paw)." · candidates = good seats · apply =
  `PlacedReminder("lycanthrope","Faux Paw")` exclusive · validation = exactly one, on a **good**
  seat. Issue: `"Lycanthrope: mark exactly one good player with Faux Paw"`.
identity: `Faux Paw` = `Effect(REGISTERS_AS evil, targetId = marked, sourceCharacterId =
  "lycanthrope", sourcePlayerId = lycanthropeSeat, until = FOREVER, endsWithSource = true)`.
  Needs `Status.registersEvil(...)` routed into every alignment check in `InfoCalc` (chef,
  empath, shugenja, undertaker, seamstress, dreamer, villageidiot, cultleader, steward, noble,
  bountyhunter, investigator/librarian/washerwoman, balloonist). `Player.isEvil` stays the
  **true** alignment for `WinCheck` — the Faux Paw player wins with good. Every affected result
  gains `"Cara registers as EVIL (Lycanthrope Faux Paw)."` and, when the Lycanthrope is
  dead/impaired, `"The Lycanthrope is dead — Cara registers as good again."` (which
  `endsWithSource` gives for free).
night.other:
  gate: `Gates.aliveHolder` only. **Impaired holders are still woken and still choose** — the
    step must say the choice has no effect (today there is no caveat at all).
  action: `ChoosePlayers(min = 1, max = 1, constraints = [ALIVE, SELF_ALLOWED],
    sort = SEAT_ORDER)`; dead seats disabled.
  effects: impaired source -> nothing, banner "no one dies and the Demon kills as normal".
    `Status.registersEvil(target)` -> nothing (name the Faux Paw explicitly if that is why).
    Otherwise: `Attack(TARGET, cause = KillCause(GOOD_ABILITY, "lycanthrope", holderId),
    respectProtection = true)` — `killOutcome` correctly lets Sailor/Innkeeper/Tea Lady/Fool
    block it while Monk/Soldier do not; + `PlaceToken("lycanthrope","Dead", TARGET,
    Expiry.NEVER)` + `PlaceToken`/`Effect(DEMON_CANNOT_KILL)` on **every Demon seat**,
    `until = DAWN`; + `RecordChoice()`.
  deferred: the block is consumed by every Demon-sourced death for the rest of the night,
    **including deferred ones** (Pukka's previous-night victim). Implement as `killOutcome`
    rule 1.5 (see Group note 1), not as a disabled button. The Demon still wakes and still
    chooses: Fang Gu jump, Imp star-pass, Al-Hadikhia sequence, Po's no-kill bookkeeping,
    No Dashii/Vigormortis poison all still resolve — only the death is suppressed, and the
    Demon must never learn it failed.
  info: the Lycanthrope learns nothing — state it so the ST does not signal back.
  visibility: nothing to anyone.
day: `DawnReport` must list the Lycanthrope's victim **and not** list a Demon victim; record the
  Lycanthrope's public claim (`LedgerEntry(kind = STATEMENT)`).
death: the victim's `DeathEvent.cause` is `GOOD_ABILITY/lycanthrope` — **not** `DEMON_ABILITY`.
  This is load-bearing for the Choirboy (`king`), the Sage and the Grandmother; today
  `SeatSheet.kt:270-272`'s single "Died at night" button records `DeathCause.DEMON` for every
  night death and corrupts all three.
ledger: `kind = CHOICE, sourceId = "lycanthrope"` each night (no different-from-last-night rule,
  but the record drives the dawn report and undo).
tests:
  - sober Lycanthrope kills a good player -> target dead with a `GOOD_ABILITY/lycanthrope`
    cause, `lycanthrope:Dead` placed, a `DEMON_CANNOT_KILL` effect exists (fails).
  - in that state, a Demon `Attack` -> `Blocked`; **and** a Pukka deferred kill -> `Blocked`.
  - dawn -> the block effect is gone, `lycanthrope:Dead` and `Faux Paw` remain.
  - poisoned Lycanthrope picks a good player -> nobody dies, no block, poison caveat present.
  - Faux Paw on the Empath's alive neighbour with a living sober Lycanthrope -> Empath reads 1
    with a Faux Paw caveat; with the Lycanthrope dead -> 0 (fails).
  - no `Faux Paw` token, or one on an evil seat -> `validateSetupState` reports it (fails).
open: **Gambler jinx** — "if the Lycanthrope is alive and the Gambler kills themself at night,
  no one else can die that night" is *broader* than this character's own block (all causes, not
  just the Demon's). It needs a game-level effect, not a seat effect; no mechanics spec covers
  a grimoire-scoped `Effect`. Lead must decide (`storytellerReminders` + a killOutcome rule 0.5
  is the cheapest route).

## magician — Magician · Experimental Townsfolk · P0:2 P1:6

today: the Magician's own row gives correct advice, then the **next two rows tell the ST to do
the opposite** — `MINION_INFO` names only the real Demon, `DEMON_INFO` only the real Minions.
Following the checklist hands evil the truth and the ability silently never happens.
data:
  - characters.json: ok (no reminders; text matches).
  - night_and_jinxes.json: **:118-121 is wrong and rules-breaking** (it tells the ST to wake the
    Magician nightly and give them a choice they do not have) — replace the
    `magician`+`lilmonsta` reason with "If the Magician is alive, the Storyteller chooses which
    Minion babysits Lil' Monsta." **Add** four missing jinxes:
    `magician`+`legion` "…during the Demon info step, Legion wake in separate groups";
    `magician`+`marionette` "If the Magician is alive, the Demon doesn't know which neighbor is
    the Marionette."; `magician`+`vizier` "If the Vizier is in play, the Magician has no ability
    but is immune to the Vizier's ability."; `magician`+`wraith` "After each execution, the
    living Magician may publicly guess a living player as the Wraith." Spy/Widow present, ok.
  - night_guide.json: **add `MINION_INFO` and `DEMON_INFO` entries** (none of the four markers
    has one) with cards `THIS IS THE DEMON`, `THESE ARE YOUR MINIONS`, `THESE CHARACTERS ARE NOT
    IN PLAY`. This is the structural reason the Magician has nowhere to live, and it also
    unblocks poppygrower, snitch, damsel, marionette, summoner and lunatic. :1096-1101 — add
    the Poppy Grower re-run tip and the Vizier exception.
setup: none.
identity: plain (the Magician never wakes and learns nothing).
night.first:
  gate: the Magician's own row (order 13) is `Fire` but **informational** — retitle
    `Magician (Ana) — does not wake. The next two steps change.` and auto-tick it when both
    modified steps are ticked. In a <7-resident game it must say the ability has no effect.
  action: none. **The real change is a content transform on two marker steps**, active when a
    seat holds `characterId == "magician"`, is **alive**, `Status.hasAbility` is true, and
    **no Vizier is in play**.
  effects (MINION_INFO): detail becomes `Wake all Minions (Bo, Cara). Show "THIS IS THE DEMON"
    and point to TWO players: Ali and Ana — in a different order each time. Do not say which is
    which.` The two names must be emitted in a **per-game-seeded** random order (stable across
    recompositions, different across games).
  effects (DEMON_INFO): the Magician is **interleaved** into the Minion list, not appended:
    `Show "THESE ARE YOUR MINIONS" and point to Bo, Ana, Cara.` **Marionette jinx:** suppress
    `NightOrder.kt:99-104`'s `". Point out the Marionette (Dan)"` clause and the sub-7
    Marionette step, replacing them with "the Demon does NOT learn which neighbour is the
    Marionette (Magician jinx)". **Legion jinx:** split `DEMON_INFO` into one row per Legion
    group. Bluffs are unaffected.
  gate exception (Vizier): run both steps exactly as today and note on the Magician's row
    `Vizier in play — the Magician has NO ability (but is immune to the Vizier).`
  info: nothing is computed *for* the Magician; a drunk/poisoned Magician (or a Drunk/Marionette
    shown the Magician token) means both steps run **normally** — needs an impairment banner
    the step cannot show today.
  show: the two marker cards above.
  visibility: this **is** the ability — Minions see two candidate Demons, the Demon sees N+1
    candidate Minions, neither is told which.
  deferred: **mid-game re-run** — when a Poppy Grower dies, the evil-meets reveal must use the
    *same* Magician-aware builder on both halves (wiki tip). `StatusEffects.kt:101` currently
    drops the Magician from that text entirely.
day: with a **Wraith** in play, a post-execution recorder `LedgerEntry(kind = STATEMENT,
  sourceId = "magician")` — "Magician's public Wraith guess: <player>"; if correct, tonight's
  Demon step carries "the Demon must choose <player>".
death: none. **Lil' Monsta:** with an alive Magician the babysitter is the **Storyteller's**
  pick — a one-tap "who babysits tonight?" on the Lil' Monsta step, and suppress any "Minions
  choose" wording.
ledger: none beyond the Wraith guess.
tests:
  - 8-player Magician + Imp + 2 Minions -> `MINION_INFO` detail contains the Magician's name
    (fails); `DEMON_INFO` detail contains it among the Minions (fails).
  - same plus a **Vizier** -> neither step mentions the Magician and the Magician's row says the
    ability is off (fails).
  - Magician + Marionette -> `DEMON_INFO` does **not** name the Marionette (fails).
  - poisoned Magician on night 1 -> both steps run normally.
  - shipped data -> the `magician`+`lilmonsta` text equals the official wording and jinxes exist
    for legion/marionette/vizier/wraith (fails).
open: Spy/Widow grimoire-view mode does not exist yet; when it is built it must remove **both**
  the Demon's and the Magician's character tokens.

## nightwatchman — Nightwatchman · Experimental Townsfolk · P0:0 P1:3

today: the step reappears every night after the ability is spent (Dawn guard blocks on it), the
target is never captured, and there is no impairment banner — a poisoned Nightwatchman's target
gets woken and told the truth.
data:
  - characters.json: ok (`reminders: ["No Ability"]`; text says "choose a player", **not** an
    alive one — dead targets are legal).
  - night_and_jinxes.json: ok (first 64, other 87; no jinxes exist).
  - night_guide.json:1102-1125: add "the chosen player may be dead", "wake them separately —
    the two must not make eye contact", and the Vortox clause. :1108/:1120 — the card must
    carry the Nightwatchman's **seat name** as well as the token.
setup: none.
identity: plain.
night.first / night.other (identical):
  gate: `Gates.aliveHolder` + `Gates.notSpent("nightwatchman", "No Ability")`; spent ->
    `Skip("used on night 2 — showed Bo")`, auto-ticked (wiki: "remove their night token").
  action: `ChoosePlayers(min = 1, max = 1, constraints = [ANY_LIVING_STATE, SELF_ALLOWED],
    sort = ALIVE_FIRST, allowNone = true, noneLabel = "Declined — no choice")`; dead chips
    shown with `†` and selectable.
  effects: `MarkSpent("nightwatchman")` (`nightwatchman:No Ability`, `Expiry.NEVER`) +
    `RecordChoice()` + `ShowCardTo(TARGET, "THIS PLAYER IS <holder name> — the Nightwatchman")`
    + `WakeEvent(ownAbility = false)` for the target.
  deferred: none.
  info: **impaired** (`Status.impairment` non-empty) -> no card is offered at all, only
    `! The Nightwatchman is POISONED — do NOT wake Bo. They learn nothing. The use is spent.`
    **Vortox** -> the target must be shown a *different* character token: `falseAlternatives`
    = a token picker over the script with **not-in-play first** (today's dialog sorts in-play
    first).
  show: one card only — delete the duplicate tray "Show token" chip; the card must name the
    seat, since "which player" is the entire point and pointing in a dark room is not an option
    on a phone.
  visibility: the chosen player learns the Nightwatchman's identity. Nothing to Demon/Minions/
    Lunatic. If the target is evil the app must not warn or hesitate.
day: a claim recorder — "X says they were shown the Nightwatchman is Y" — with a one-line
  verdict from `Memory`: `Matches your record (night 2, Ana -> Bo)` /
  `No Nightwatchman reveal has happened` / `Matches, but the Nightwatchman was poisoned`.
  `DayBriefing.Slot.DAY_START` on the morning after: "Bo learned who the Nightwatchman is."
death: none.
ledger: `kind = CHOICE, sourceId = "nightwatchman"` + `kind = TOLD` on the target's seat —
  the only thing that survives an undo/redo today is nothing.
tests:
  - holder marked `No Ability` -> no `nightwatchman` step on cycle 2 (fails).
  - sober pick -> exactly one `No Ability` token and a recorded choice with the cycle.
  - poisoned pick -> still spent, "target learns nothing", **no** reveal card (fails).
  - poisoned holder -> the step's caveats contain the poison line (fails — `InfoCalc.compute`
    returns null for this id).
  - dead target -> accepted (no alive-only constraint).
  - declined -> no token placed and the step reappears next night.
  - dawn+dusk -> the `No Ability` token survives.
open: "Mark spent" is currently offered before any target is chosen — easy to tap after a
  decline. The resolver must own the spend.

## noble — Noble · Experimental Townsfolk · P0:0 P1:4

today: identical shape to the Knight — nothing at setup, and `InfoCalc.noble` restates the task
("Point to 3 players: exactly 1 evil, 2 good") plus a bare list of evil players. Three `Know`
tokens on three good players raise no complaint anywhere.
data:
  - characters.json: ok (`reminders: ["Know","Know","Know"]`).
  - night_and_jinxes.json: ok (first 59, absent from otherNight; no jinxes exist).
  - night_guide.json:1126-1131: add "the Recluse may be used as your 1 evil; the Spy may be used
    as one of your 2 good" and "under a Vortox the 3 must not contain exactly 1 evil".
setup: `noble.know` · PICK_PLAYERS(3) · "Noble — mark 3 players: exactly 1 evil, 2 good." ·
  candidates = all seats, split into good / evil panels with live validation (`2 good ✓ /
  1 evil ✓`) · apply = `PlacedReminder("noble","Know")` ×3 (cap 3) · validation = exactly three
  tokens and — using **registration-aware** alignment (`Status.registersEvil`, so a Recluse or a
  Lycanthrope Faux Paw counts) — exactly one on an evil-registering seat, **unless** a Vortox is
  in play, when exactly one must **not** be. Issue: `"Noble: mark 3 players — exactly 1 evil"`.
identity: plain.
night.first:
  gate: `Gates.aliveHolder`; also a `variant = "first"` re-run when a seat first becomes the
    Noble mid-game (today: no step, ever).
  action: `ShowInfo(targetsNeeded = 0)`.
  effects: three `noble:Know` tokens, `maxCopies = 3`, `Expiry.NEVER`.
  info: tokens placed -> `Point to Bo, Cara and Dan. Exactly 1 of them is evil.` Missing ->
    `Pick 3: 2 good + 1 evil.` with `Good: …` / `Evil: …` chip rows. Detail:
    `Evil in play: Ali (Poisoner), Eve (Imp).` + `Registering evil: also Yara (Recluse) if you
    choose.` Misregistration, Noble-specific (suppress the generic lines):
    `You may use Yara (Recluse) as the 1 evil.` / `You may use Ali (Spy) as one of the 2 good.`
    Impaired -> `Any 3 players are legal — 0, 2 or 3 evil is fine.` and validation goes quiet.
    Vortox -> `the 3 must NOT contain exactly 1 evil (use 0, 2 or 3)`, suggested triple flips.
    Lycanthrope Faux Paw -> `Cara registers as evil (Faux Paw) — she can be your 1 evil.`
  show: shared `ShowCard.PlayerCard` (see `steward`).
  visibility: nothing to evil.
day: `LedgerEntry(kind = STATEMENT, sourceId = "noble")` — the Noble almost always names their
  three publicly; cross-check against the tokens and the snapshot impairment.
ledger: snapshot night-1 impairment on the `TOLD` entry.
tests:
  - Noble in play, no tokens -> `validateSetupState` reports it (fails).
  - three tokens all on good players -> "exactly 1 evil" issue (fails); two on evil -> issue.
  - legal triple -> the headline names the three players (fails — static headline).
  - Recluse + Spy in play -> Noble-specific caveat phrasings (fails).
  - Vortox -> instructs 0/2/3 evil and the validator accepts a 0-evil triple (fails).
  - a seat becomes the Noble on cycle 3 -> a Noble step appears (fails).
  - Faux Paw on a good player with a living sober Lycanthrope -> that player counts as the
    1 evil (fails).
  - dawn+dusk -> all three tokens remain.
open: none. (The `Know` label is shared with the Knight but `sourceId` separates them; the seat
  display should still show the source character in a Knight+Noble game.)

## pixie — Pixie · Experimental Townsfolk · P0:2 P1:3

today: the "Mad as" show card works and is genuinely good — and then **nothing is recorded**.
The gained ability never wakes, `Has Ability` is a dead token no code reads, the marked
character's death fires no trigger, and `pixie:Mad` falsely trips the Cerenovus nomination
warning.
data:
  - characters.json:1506: ok (`reminders: ["Mad","Has Ability"]`).
  - night_and_jinxes.json: ok (first 42 only; the gained ability wakes on the **gained
    character's** row, not the Pixie's, so no other-night slot is needed; no jinxes exist).
  - night_guide.json:1132: add an `other` entry — "Only if the Pixie has gained an ability. Run
    the gained character's night action for the Pixie, from the Pixie's own seat."
    (`NightGuide.forStep("pixie", false)` returns null today.)
setup: `pixie.madAs` (already in the ux/setup-and-home table) · PICK_CHARACTER · "Pick an in-play
  Townsfolk. Show that token. They must be mad they are it." · candidates = **in-play
  Townsfolk** alphabetically, with a collapsed "not in play" section that auto-expands when the
  Pixie is impaired (a drunk Pixie may legally be shown a not-in-play Townsfolk — one of the
  wiki's three examples) · apply = `PlacedReminder("pixie","Mad", characterId = <chosen>)` on
  the Pixie's own seat, mirrored onto the holder's seat when exactly one seat holds it ·
  validation = required.
identity: **conflict resolved** — the character file proposed `GameState.pixieMad: Map<Long,
  String>`; use `PlacedReminder.characterId` (records-and-memory §A) instead, so no new state
  and undo works for free. `Has Ability` becomes
  `Effect(HAS_ABILITY, targetId = pixieSeat, sourceCharacterId = "pixie", until = FOREVER,
  endsWithSource = false)` — permanent, and unaffected by the dead player being resurrected.
night.first:
  gate: `Gates.aliveHolder`. Re-run as `variant = "first"` if a Pixie is created mid-game.
  action: `ChooseCharacter(pool = SCRIPT filtered TOWNSFOLK, allowNone = false)`.
  effects: `PlaceToken("pixie","Mad", …, characterId = chosen)` + seat note
    `Mad that they are the <Name>` (mirrors the Drunk/Lunatic note pattern).
  info: the Pixie learns a **character, never a player**. Impaired -> `! Pixie is drunk/poisoned
    — you may show a NOT-in-play Townsfolk; they will not gain a working ability.`
  show: `YOU ARE MAD THAT YOU ARE` + token (exists; pre-select the stored choice).
  visibility: nothing to evil.
night.other:
  gate: **not a Pixie step.** Once `HAS_ABILITY` is active, `NightPlan` must emit the *gained
    character's* step with `playerIds = [pixieSeat]`, badged `<Name> has this ability (Pixie)`,
    and `InfoCalc.compute(holderId = pixieSeat)` must compute **from the Pixie's own seat**
    (their neighbours, not the dead holder's). Requires
    `GameState.abilityHolders(characterId)` — the same hook Boffin/Alchemist/Cannibal/
    Philosopher need. A once-per-game gained ability gets its own spent token on the Pixie's
    seat.
death: `DeathTrigger(characterId = "pixie", matches = event.characterIdAtDeath == the Pixie's
  marked character id **(the originally-learned one, not the victim's current character)**,
  produce = Prompt(at = NOW for a day death / DAWN for a night death, kind = PLACE_EFFECT,
  title = "<Character> just died. Was <Pixie> mad enough about being the <Character>?
  [Grant ability] [No]"))`. Granting: remove `pixie:Mad`, add `pixie:Has Ability` **on the
  Pixie's seat**, append to the seat note, fully undoable. Once only — never re-prompt if
  `Has Ability` is present. If the Pixie is impaired at the grant, still grant (the token is a
  record) and warn that the gained ability malfunctions while that lasts.
day: `DayBriefing.Slot.DAY_START` — "watch whether Alex keeps claiming Empath" while a madness
  judgement is pending. **Bug to fix here:** `StatusEffects.kt:162` matches the *label* `"Mad"`
  regardless of `sourceId`, so a `pixie:Mad` seat wrongly triggers the Cerenovus nomination
  warning — scope it to `sourceId in {"cerenovus","harpy"}` (also fixes Sentinel/Harpy).
ledger: `kind = CHOICE, sourceId = "pixie", characterIds = [madCharacter]` on night 1;
  `kind = RULING` for the madness verdict.
tests:
  - recording the mad character -> the Pixie's `pixie:Mad` carries `characterId == "empath"` and
    the Empath's seat is mirrored.
  - killing the marked Empath -> a Pixie prompt naming both (today: silent).
  - the marked seat later becomes the Chef, then dies -> the prompt still says **Empath**.
  - `Has Ability` on the Pixie with the real Empath dead -> an `empath` step exists with
    `playerIds == [pixieSeat]` (today: no step at all) and its info is computed from seat 0.
  - `pixie:Mad` on a seat -> `nominationWarnings` contains no "Cerenovus" note (today it does).
  - both tokens survive dawn and dusk.
open: what if the marked player dies while the **Pixie is dead**? Wiki silent — place
  `Has Ability` anyway; it does nothing unless they are resurrected.

## poppygrower — Poppy Grower · Experimental Townsfolk · P0:3 P1:4

today: `MINION_INFO` and `DEMON_INFO` are still generated **fully populated with names** on
night 1, they carry the one-tap bluffs chip, and the Dawn guard forces the ST to tick them.
When the Poppy Grower dies the app fires one prose sentence and forgets: the reveal night is
never scheduled and its row stays unpopulated prose.
data:
  - characters.json:1521: ok (`reminders: ["Evil Wakes"]`; both reminder strings correct).
  - night_and_jinxes.json: **add** `poppygrower`+`summoner`: "If the Poppy Grower is alive on
    the 3rd night, the Summoner chooses which Demon but not which player." Present and correct:
    marionette(:95), lilmonsta(:124), spy(:145), widow(:150). Order ok (first 11, other 8).
  - night_guide.json:1145: the `other` entry's last clause is a run-on that contradicts the wiki
    — restate the impaired case as *"if the Poppy Grower's ability was not working when they
    died, evil already knew each other and nothing happens."*
setup: `poppygrower.skipInfo` · ACK (in the ux table) — but the suppression must be **derived**,
  not acknowledged: see gates below.
night.first:
  gate: `poppyGrowerActive` (a `poppygrower` seat, alive, `Status.hasAbility`) ->
    `MINION_INFO` and `DEMON_INFO` both become `StepGate.Skip("Poppy Grower")`, rendered as a
    **visible greyed auto-ticked row** (`Minion & Demon info — SKIPPED (Poppy Grower)`), never
    silently dropped — silent removal reads as a bug. Replaced by a `DEMON_BLUFFS_ONLY` step
    (night-engine §2 already names it), which is the Poppy Grower's own row repurposed.
  action/effects: the row's `playerIds` = the Demon seats; detail `Wake the Demon (<Demon>).
    Show "THESE CHARACTERS ARE NOT IN PLAY" and 3 not-in-play good tokens: <bluffs>. Minions
    learn NOTHING tonight — do not wake them.` The **bluffs show-card chip must move onto this
    row** — today it is attached to `DEMON_INFO` only, so with a Poppy Grower in play there is
    no way to show the bluffs at all.
  gate exception: an **impaired** Poppy Grower does **not** suppress — emit normal
    `MINION_INFO`/`DEMON_INFO` plus "evil learns each other as normal tonight (optional rule)";
    that Poppy Grower dying later triggers **no** re-reveal (wiki example).
  visibility: the Marionette is **not** pointed out on night 1 — the jinx defers it to the
    reveal: `the Demon does NOT learn them tonight (Poppy Grower jinx).`
  jinxes: **Lil' Monsta** — `Wake Minions ONE BY ONE (<seat order>) until one takes the token —
    they must not see each other.` **Spy/Widow** — while `poppyGrowerActive` both night rows
    read `do NOT show the grimoire tonight; wake them, show nothing, put them back to sleep`,
    reverting automatically on the Poppy Grower's death. **Summoner** — night 3 with a living
    Poppy Grower: `the Summoner chooses which DEMON but NOT which player. You choose the player.`
night.other:
  gate: **no row at all while the Poppy Grower is alive** (today: every night, and the Dawn
    guard nags). Fire only when the reveal is pending — a `poppygrower` seat is dead (or
    impaired/left play under the optional rule) **and** `DeathEvent.impairedAtDeath == false`
    **and** no `poppygrower:Evil Wakes` has been consumed.
  action: none — it is a scripted two-part wake, `WakeStyle.FIRST_NIGHT`.
  effects: on the qualifying death, auto-place `PlacedReminder("poppygrower","Evil Wakes")` on
    the Poppy Grower's own seat (undoable). `Expiry.NEVER`; consumed once.
  deferred/insertion: (a) the canonical other-night slot 8 when they were already dead at the
    start of the night; (b) **immediately before `DAWN`** when they died earlier *this* night
    and their slot has passed — night-engine's "insert-after-cursor" rule.
  detail: `1) Wake the Minions together: <names> (NOT <Marionette> — never woken for this). Eye
    contact. Show "THIS IS THE DEMON", point to <Demon>. Sleep. 2) Wake <Demon>. Show "THESE ARE
    YOUR MINIONS", point to <Minions>` + with a Marionette ` and to <Marionette>.`
  visibility: Minions learn the Demon; the Demon learns the Minions **and** the Marionette; the
    Marionette learns nothing. **With a Magician alive, use the Magician-aware builder** (wiki).
death: `DeathTrigger("poppygrower")` -> `Prompt(at = TONIGHT, kind = RUN_FIRST_NIGHT)`. Fix the
  `deathNotes` wording: execution -> "…learn each other TONIGHT — the app has added the step";
  night death -> "…before dawn"; impaired at death -> "evil already knew each other; no reveal."
ledger: `kind = RULING` when the optional drunk/poisoned/left-play rule is invoked.
tests:
  - 8-player game with a Poppy Grower -> no `MINION_INFO` and no `DEMON_INFO` step (today both).
  - the `poppygrower` first-night step names the Demon and all three bluffs, `playerIds` = Demon.
  - **impaired** Poppy Grower -> both info steps present; living Poppy Grower on cycle 3 -> no
    `poppygrower` step (today: every night).
  - executed on day 2 -> a populated reveal step on cycle 3 listing Minions + Demon; killed at
    night -> inserted immediately before `DAWN`; reveal consumed -> no step on cycle 4;
    impaired-at-death -> no reveal at all.
  - Marionette excluded from the woken Minions but named in the Demon clause; the Spy step says
    "do NOT show the grimoire" while the Poppy Grower lives, and not after.
open: the sheet says "died today or tonight" but not **where in the night order** to run a
  same-night reveal. Encoded here as "as soon as possible; before `DAWN` if their slot has
  passed" — flag to the user as a judgement call.

## preacher — Preacher · Experimental Townsfolk · P0:2 P1:4

today: a preached Minion's night step is still emitted, unmarked and actionable, and the Dawn
guard demands it be ticked — the ST is actively led to run an ability that does not exist. And
nothing gives the ability back when the Preacher dies or is poisoned.
data:
  - characters.json:1535: ok (`reminders: ["No Ability","No Ability","No Ability"]`). Consider
    normalising the casing to `"No ability"`; any predicate must compare case-insensitively
    **and key on `sourceId`**.
  - night_and_jinxes.json: **all three jinxes are missing — add** `preacher`+`legion` "If the
    Preacher chooses Legion, Legion keeps their ability, but the Preacher might learn they are
    Legion."; `preacher`+`summoner` "If the living Summoner has no ability, the Storyteller has
    the Summoner ability."; `preacher`+`vizier` "If the Vizier loses their ability, they learn
    this, and cannot die during the day." Order ok (first 23, other 11).
  - night_guide.json:1175: add the Marionette exception and the "while the Preacher is alive"
    lapse to **both** `first` and `other`; add a second card
    `{"label":"Selected you","kind":"message","text":"THIS CHARACTER SELECTED YOU"}`.
setup: none.
identity: preached Minions keep their character; only the ability is suppressed.
night.first / night.other (identical, every night):
  gate: `Gates.aliveHolder` (a dead Preacher does not act, and their tokens are dormant anyway).
  action: `ChoosePlayers(min = 1, max = 1, constraints = [ANY_LIVING_STATE, SELF_ALLOWED])` —
    **dead players are legal targets** (wiki, explicit). No different-from-last-night rule.
    Sort: already-preached seats **last**, marked "already preached" (legal but pointless).
  effects (sober, healthy, living Preacher, target is a Minion): `PlaceToken("preacher",
    "No Ability", TARGET, maxCopies = 3, exclusive = false)` **plus**
    `Effect(NO_ABILITY, targetId = minion, sourceCharacterId = "preacher", sourcePlayerId =
    preacherSeat, until = FOREVER, endsWithSource = true)` — `endsWithSource` is the whole rule:
    §2 of status-model makes the suppression lapse while the Preacher is impaired and end when
    they die, with no extra code. Then `ShowCardTo(TARGET, "THIS CHARACTER SELECTED YOU")` ->
    the Preacher token, **unless** the target is the **Marionette** (never woken for abilities
    that would confirm they are a Minion): place the token, show `! Marionette — do NOT wake
    them. They lose their ability but learn nothing.` Non-Minion target -> `Nothing happens. Do
    not wake anyone.` Impaired Preacher -> let them point, **place nothing, wake nobody**; the
    placement action is disabled, not merely discouraged.
    Jinx targets: Vizier -> also `Effect(DAY_IMMUNE)` + "the Vizier learns they lost their
    ability and cannot die during the day". Legion -> **no token**, "Legion KEEPS their
    ability; you may tell the Preacher they are Legion." Summoner -> "while the living Summoner
    has no ability, YOU have the Summoner ability on night 3."
  deferred: none of its own — suppression is a **standing** condition re-evaluated on every
    `NightPlan` build. expiry: `preacher:No Ability` is `Expiry.NEVER` (neither table).
  info: the Preacher learns nothing; the Minion learns only "the Preacher chose you". The row
    should list who the Minions are (every other info row names its seats; this one does not)
    and carry a standing summary: `Currently preached: <names> (active / dormant — the Preacher
    is dead|poisoned).` visibility: nothing to the Demon.
  **suppression of the victim's step:** in `NightPlan`, a holder with no ability yields
    `StepGate.Skip("<Minion> has no ability (Preacher (Ana))")` — rendered as a **visible
    greyed auto-ticked row with `[Run anyway]`**, reusing the existing "All holders are dead"
    presentation. It must flip back automatically the moment the Preacher dies or is poisoned,
    stating why: `<Minion> has their ability back — the Preacher is dead.`
death: `DeathTrigger("preacher")` -> `Prompt(at = DAWN, kind = ANNOUNCE)` +
  `deathNotes` gains "Preacher: every Minion marked 'No Ability' by them gets their ability
  back", **listing the affected seats by name**. `DayBriefing.Slot.DAY_START`:
  `<Minion> (and …) have their abilities back — the Preacher is dead.` When a poison token is
  placed on the Preacher, the seat sheet says the same and the tokens render dimmed/dormant.
ledger: `kind = CHOICE, sourceId = "preacher"` every night — the history of who was preached
  when is the only way to audit a dormant/active flip.
tests:
  - preached Poisoner + living sober Preacher -> the `poisoner` step is SKIP (today: a normal
    actionable row); the Preacher executed or poisoned -> the step is normal again, and says why.
  - `deathNotes(preacher)` names every freed Minion (today: no `preacher` case).
  - `preacher:No Ability` survives dawn and dusk.
  - `hasAbility` distinguishes `preacher:No Ability` (suppressed -> step SKIPped) from
    `professor:No ability` (spent -> step still emitted, badged "spent").
  - preaching a non-Minion places nothing; an impaired Preacher's choice places nothing.
  - the Marionette is preached **without** being woken.
open: the tray silently recycles the 4th `No Ability` placement, un-preaching the first Minion —
  the 3-token cap is a box limit, not a rules limit, so raise `maxCopies` or confirm the move.
  Wiki silent on whether a Minion preached twice is woken again, and on whether a preached
  player who *stops* being a Minion (Pit-Hag) keeps the suppression.

## princess — Princess · Experimental Townsfolk · P0:2 P1:4

today: **zero engine logic** (`grep princess engine/src app/src` finds only data). The condition
is fully computable from existing state and is simply never computed; the Demon kill panel
offers an enabled, unqualified "<name> dies" button on the Princess night. The prose row appears
every night from night 2 regardless.
data:
  - characters.json:1551: `otherNightReminder` omits "1st day" entirely — change to "If the
    Princess nominated the player who was executed **on their 1st day**, wake the Demon as
    normal, but no one dies to the Demon's ability." `reminders: ["Doesn't Kill"]` ok.
  - night_and_jinxes.json: **both jinxes missing — add** `princess`+`alhadikhia` "If the
    Princess nominated & executed a player on their 1st day, no one dies to the Al-Hadikhia
    tonight." and `princess`+`cannibal` "If the Cannibal nominated, executed, & killed the
    Princess today, the Demon doesn't kill tonight." Order ok (other 35, before every Demon).
  - night_guide.json:1199: **factual error — the shipped text states the opposite of the wiki.**
    Replace "If the Princess was drunk or poisoned during that day, the Demon kills as normal"
    with "What matters is the Princess's state **now, at night**." Add "the executed player does
    not have to have died", "exiles do not count", "non-Demon kills still happen".
setup: none.
identity: "1st day" = the first day the seat holds `princess` — use `Player.standingSince`
  (status-model §1), defaulting to day 1; a mid-game Princess gets a fresh window.
day: **this is a day-engine character.**
  - `checkNomination` -> `NominationTrigger(kind = WARN, sourceId = "princess")` when the
    nominator is the Princess on their 1st day: "Princess nominates on their 1st day — if
    <Nominee> is executed today, the Demon does not kill tonight."
  - `executionConsequences(...)` -> `ExecutionConsequence(sourceId = "princess")` fired from the
    **single `GameActions.execute` funnel** (today three separate call sites kill with zero
    Princess awareness). Condition: `ExecutionRecord.day == princessFirstDay`, `via != EXILE`,
    and a `Nomination` that day with `nominatorId == princessSeat && nomineeId ==
    record.playerId`. **`outcome == SURVIVED` still counts** — "the executed player does not
    have to die"; this is precisely why day-engine's `ExecutionRecord` is required rather than
    deriving executions from deaths.
  - effect: `Effect(DEMON_CANNOT_KILL, targetId = <Demon seat>, sourceCharacterId = "princess",
    until = DAWN)` + `PlacedReminder("princess","Doesn't Kill")` on the Demon, exclusive.
    Confirm inline: `Princess: the Demon does not kill tonight. "Doesn't Kill" placed on <Demon>.`
    `DayBriefing.Slot.DUSK`: "Tonight: the Demon does not kill (Princess)."; day-1 `DAY_START`:
    "A Princess is in play — watch who nominates."
  - **Cannibal alternative trigger**: a living Cannibal who nominated, executed and killed the
    Princess today places the same effect — a second branch of the same predicate.
night.other:
  gate: emit a `princess` row **only** when the effect/token exists (today: every night from
    night 2, training the ST to tick without reading — which is how the real bug happens).
  action: none — the row is purely a modifier on the Demon's step.
  effects: the Demon's step gains ` — PRINCESS: wake them and let them choose as normal, but
    NOBODY DIES to the Demon's kill. Other Demon effects still happen.` (same annotation
    mechanism `NightOrder` already uses for the Exorcist and Lunatic). The Demon's action keeps
    its **chip picker enabled** (the choice must still be recorded for Lunatic/Mathematician/
    Fang Gu reasoning) but the kill resolves to `Blocked` via `killOutcome` rule 1.5 — Fang Gu
    jump, Imp star-pass, No Dashii/Vigormortis poison and Vortox falsification stay live.
    **Al-Hadikhia jinx:** the block widens to "**no one** dies to the Al-Hadikhia tonight".
    Non-Demon kills (Assassin, Godfather, Gossip, Witch, Fearmonger, Vigormortis) stay enabled.
  expiry: `princess:Doesn't Kill` -> **`EXPIRES_AT_DAWN`** (`Expiry.DAWN`); it applies to exactly
    one night. Today it is in neither table and nothing reads it.
  info: the Princess learns nothing; the Demon is told nothing about why, and no card is shown
    to anyone. **Impairment is evaluated at night, at the moment the step is reached** — a
    Princess drunk by day and sober now still blocks; sober by day and drunk now does not.
ledger: `kind = ANNOUNCE` for the dusk line; the trigger itself is derived from `executions` +
  `nominations`, not stored.
tests:
  - day-1 nomination by the Princess + execution -> the trigger fires; nominated by someone
    else, or on day 2, or `isExile` -> it does not.
  - **the nominee was executed but survived (Devil's Advocate)** -> the trigger still fires
    (fails today: a death-less execution leaves no trace in state).
  - the `Doesn't Kill` token lands on the Demon and nowhere else; it is gone after `advancePhase`.
  - no qualifying nomination -> no `princess` row on cycle 3 (today: present every night).
  - with the token, the `imp` step's detail contains "PRINCESS" and a Demon `Attack` is
    `Blocked`; with the Princess poisoned **at night**, it is not.
open: must the Princess be **alive** at night? Wiki silent — the drunk-at-night clarification
  implies evaluation at night (suggesting no), but the Cannibal jinx (the Princess is killed
  that very day) implies yes. Surface as an advisory with both buttons enabled and "no kill"
  pre-selected; never hard-code.

## shugenja — Shugenja · Experimental Townsfolk · P0:0 P1:3

today: the **best-implemented character in the group** — `InfoCalc.shugenja` walks both
directions correctly, wraps the circle, excludes self, honours `alignmentFlipped`, and handles
equidistance. The gaps are all around it: no way to deliver the answer on a phone, an **empty**
"False info to show instead" panel, and no record of what was said.
data:
  - characters.json:1565: ok. night_and_jinxes.json:356: ok (first 61 only; no jinxes exist).
  - night_guide.json:1205: `"shows": []` -> add
    `[{"label":"Clockwise","kind":"clockwise"},{"label":"Anti-clockwise","kind":"anticlockwise"}]`;
    extend `NightGuide.VALID_KINDS` (`ScriptParserTest.kt:150-158` enforces the pairing).
setup: none.
identity: plain.
night.first:
  gate: `Gates.aliveHolder`; also a `variant = "first"` re-run when a Shugenja is created
    mid-game (absent from `otherNight`, so today the working calculator is **unreachable**).
  action: `ShowInfo(targetsNeeded = 0)`.
  effects: none, no tokens.
  info: return a **structured** result — `direction: CW | CCW | EITHER | NONE`, `cwSteps`,
    `ccwSteps`, `cwPlayer`, `ccwPlayer` — so the UI can render a card and record what was said.
    Equidistant headline: `Equidistant (<n> steps each way) — YOUR CHOICE. <CW> clockwise,
    <CCW> anti-clockwise.` (The Shugenja is never told their info was arbitrary.)
    **Misregistration, targeted:** emit a flip note **only** for a Recluse closer than the
    current answer in its direction, or a Spy that *is* the current answer —
    `If <Name> (Recluse, 1 step clockwise) registers as EVIL, the answer becomes CLOCKWISE.` /
    `If <Name> (Spy, 2 steps anti-clockwise) registers as GOOD, the answer becomes CLOCKWISE
    (3 steps).` Today the generic Spy/Recluse lines fire for seats 6 steps away that cannot
    change anything (the same over-warning hits `steward`, `knight`, `noble`, `chef`).
    State the counting assumption as a caveat when any seat is dead or a Traveller:
    `Counting all seats, including dead players and Travellers.`
    Guard `indexOfFirst == -1` -> `"Select the Shugenja's seat first"` (today the modular
    arithmetic silently reads the wrong seats).
  show: **new `ShowCard.DirectionCard(clockwise: Boolean)`** — full-screen arrow (↻ / ↺) plus
    `CLOCKWISE` / `ANTI-CLOCKWISE`. This is the piece that makes the character usable on the
    PWA; every other info role already has a card. Badge the true chip `✓ true` so a lie is a
    deliberate tap.
  info (false): when impaired, No-Dashii-adjacent, the Drunk, or under a **Vortox**,
    `InfoResult.falseAlternatives` must contain the **opposite direction card**. Generalise
    `NightScreen.kt:904-930` so alternatives come from the calculator instead of being inferred
    from the headline string — today the red "False info to show instead:" heading renders with
    an **empty chip row** for every non-numeric, non-yes/no info role (`shugenja`, `steward`,
    `knight`, `noble`, and more).
  visibility: nothing to evil.
ledger: `LedgerEntry(kind = TOLD, sourceId = "shugenja", shown = "ANTI-CLOCKWISE",
  verdict = TRUE|FALSE, impaired = …)` fired when the card is shown — the arbitrary/false answer
  is exactly the case where the record is the only thing keeping the game consistent.
tests:
  - Recluse 1 step CW, real Minion 3 steps CCW -> headline `ANTI-CLOCKWISE (3 steps)` **and** a
    caveat saying a Recluse registering evil flips it to CLOCKWISE (today: a generic note).
  - the same Recluse 4 steps away with a Minion 1 step CCW -> **no** flip caveat (today: one).
  - the Spy is the answer -> a caveat gives the answer with the Spy skipped.
  - the result exposes `direction == CCW` as a value, not just a string.
  - poisoned Shugenja, true answer CLOCKWISE -> `falseAlternatives` contains the
    ANTI-CLOCKWISE card (today: an empty chip row).
  - a stale `holderId` -> "Select the Shugenja's seat first", not a computed direction.
open: does the count include dead players and Travellers? Wiki silent; the app already counts
  them — surface the assumption as a caveat rather than hiding it.

## steward — Steward · Experimental Townsfolk · P0:0 P1:5

today: the step says "Point to the player marked 'Know'" — but nothing ever asked the ST to mark
anyone, the calculator ignores the token even when placed, and the Spy (the wiki's own canonical
Steward answer) is **excluded** from the offered list because `!ctx.isEvil` uses true alignment.
data:
  - characters.json:1577: ok. night_and_jinxes.json:352: ok (first 57 only; no jinxes exist).
  - night_guide.json:1211: `"shows": []` -> add `[{"label":"This player is good","kind":"player"}]`;
    extend `NightGuide.VALID_KINDS` with `"player"`.
setup: `steward.know` · PICK_PLAYER · "<Steward> is the Steward. Which good player do they
  learn?" · candidates = all **good** seats, plus a clearly separated **"registers as good"**
  group containing any Spy (and, under a Vortox, the whole evil team, since the info must be
  false) · apply = `placeExclusiveReminder(PlacedReminder("steward","Know"))` + seat note
  `Knows <Name> is good` · validation = one `steward:Know` token exists. Issue: `"Steward: mark
  one good player with the 'Know' reminder"`. Follow the `fortuneteller.herring` pattern exactly,
  including its "the guard only advises" escape.
identity: plain.
night.first:
  gate: `Gates.aliveHolder`. Also a `variant = "first"` re-run on the night a Steward is created
    mid-game — wiki: "If created mid-game, then the Steward learns their information that night
    instead." Today `steward` is absent from `otherNight`, so **no row is emitted at all**; the
    row must be labelled `Steward — first-night info (created tonight)` and run the KNOW prompt
    inline. (This is the same defect as `knight`, `noble`, `shugenja`, `pixie` and the user's
    Professor complaint.)
  action: `ShowInfo(targetsNeeded = 0)` — the choice is the token, made before the step.
  effects: `steward:Know`, exclusive, `Expiry.NEVER`.
  info: token placed -> `headline = "Point to <Name>"`, `detail = "<Name> is the <Character>
    (good)"`; when the marked seat is **not** good, a loud caveat `! <Name> is EVIL — is that
    deliberate (Spy / Vortox / poisoned Steward)?` (legal, and the wiki's example).
    No token -> `Mark a good player with 'Know' first` + the good list, **plus** a separate line
    `Registers as good: <Spy names>`; the Steward's own seat sorted last and labelled "(the
    Steward — pointless)"; dead seats suffixed `†`. Narrow `misregistrations` to a Spy that is a
    *candidate*; the Recluse gets one line only ("may register as evil — you may treat them as
    not a legal answer"), since a Recluse cannot make a good answer wrong here.
  show: **new `ShowCard.PlayerCard(prefix, playerId)`** — full-screen seat name (and circle
    position). This is the missing generic "point at a player" primitive and it pays for itself
    across `steward`, `noble`, `knight`, `sage`, `investigator`, `washerwoman`, `librarian`,
    `bountyhunter`, `grandmother`, `shugenja`.
  info (false): impaired or Vortox -> `falseAlternatives` = a `PlayerCard` per **evil** seat
    (today the panel says "Townsfolk info must be FALSE" and then lists only good players, with
    an empty false-info chip row).
  visibility: nothing to evil.
ledger: `kind = TOLD, sourceId = "steward", targetIds = [shown]` — once the token is placed it is
  a record; if the ST moves it, only the ledger remembers what was actually said.
tests:
  - Steward in play with no `steward:Know` -> `validateSetupState` reports it (today: empty).
  - `Know` on seat 4 -> the headline is `Point to <seat 4>` (today: the generic prompt).
  - `Know` on the Spy -> a caveat flags the marked player as evil **and still permits it**.
  - no token, Spy in play -> the detail lists the Spy under "registers as good" (today: omitted).
  - the token moves rather than accumulating, and survives dawn and dusk.
  - poisoned Steward -> `falseAlternatives` contains a card for each evil seat (today: empty).
  - a Steward assigned on cycle 3 -> a `steward` step exists, labelled as catch-up first-night
    info (today: no row at all).
open: may the KNOW token sit on the Steward themselves, and must the shown player be alive?
  Wiki silent; irrelevant on night 1, relevant for a mid-game Steward.

## villageidiot — Village Idiot · Experimental Townsfolk · P0:3 P1:3

today: three Village Idiots share **one** target picker, **one** computed answer and **one**
impairment caveat — taken from `playerIds.firstOrNull()`. If the drunk one is the 2nd or 3rd
seat, the app shows a clean true answer with **no warning, every night of the game**. Nothing
asks which one is drunk, and last night's target is restored on subsequent nights.
data:
  - characters.json:1591: ok (`setup: true`, `reminders: ["Drunk"]`, bracket text current).
    Add `Character.perHolderStep = true` (night-engine §Data changes).
  - night_and_jinxes.json: **both jinxes missing — add** `villageidiot`+`boffin` "If there is a
    spare token, the Boffin can give the Demon the Village Idiot ability." and
    `villageidiot`+`pithag` "If there is a spare token, the Pit-Hag can create an extra Village
    Idiot." Order ok (first 62, other 84).
  - night_guide.json:1217: add "if there is only one Village Idiot in play, they are sober" and
    "the drunk mark never moves" to both `first` and `other`; keep the two alignment cards.
setup: `villageidiot.drunk` (already in the ux table) · PICK_PLAYER · "Three Village Idiots:
  Ana, Bo, Cy. One of the extras is drunk — which one? This never changes." · candidates = the
  Village Idiot seats · apply = `addReminder(PlacedReminder("villageidiot","Drunk"))` + seat
  note `Drunk Village Idiot (permanent)` · validation (three separate issues): >=2 VIs and no
  `Drunk` token -> "mark exactly one"; >=2 VIs and more than one -> "only one may be marked";
  exactly 1 VI **with** a `Drunk` token -> "a lone Village Idiot is sober — remove it".
  **`randomBag` can never produce a second or third Village Idiot** (it draws distinct
  characters), so `DUPLICABLE` is honoured by the validator and never by the generator — add an
  explicit Setup toggle "extra Village Idiots: 0/1/2" rather than randomness; it is a
  Storyteller choice, not a die roll.
identity: `villageidiot:Drunk` = `Effect(DRUNK, targetId = thatSeat, sourceCharacterId =
  "villageidiot", until = FOREVER, endsWithSource = false)` — **it never moves and never ends**:
  it survives every sober VI leaving play and survives another VI becoming drunk by other means.
  It must therefore **not** be placed by the exclusive-reminder path (a stray tap in the tray
  relocates the setup decision today, with no confirmation).
night.first / night.other (identical, every night):
  gate: **per holder** — `StepKey("villageidiot", playerId)`, `perHolder = true`. Each holder's
    gate is `Gates.aliveHolder`; a dead VI's sub-panel renders greyed `Dead — does not act.`
  action: `ChoosePlayers(min = 1, max = 1, constraints = [ANY_LIVING_STATE, SELF_ALLOWED])`
    **per holder**, chosen independently. No different-from-last-night rule; show
    `last night: Bo` as a faint hint but **never pre-select it** (today's
    `rememberSaveable(step.id)` is not keyed on `state.cycle`, so the stale chip and a stale
    answer are shown as if they were tonight's — the same bug hits Dreamer, Ravenkeeper,
    Grandmother, Fortune Teller, Seamstress, Chambermaid).
  effects: none. No tokens are placed by the nightly action.
  info: per holder — `<Target> is GOOD|EVIL` from registration-aware alignment (so a Lycanthrope
    Faux Paw target reads EVIL), with the **target's** Spy/Recluse note only (already correct).
    Each sub-panel gets its own `Status.impairment` caveats, its own show chips and its own done
    tick — which is the entire fix for the P0.
  show: the existing `AlignmentCard` chips, **badged** `✓ true` / `false` against the computed
    answer. For the drunk holder, invert the badges and colour the panel: `This Village Idiot is
    DRUNK — give false info (or true, your choice).` The rules let the ST tell the drunk VI the
    truth, so the app must **offer** the lie, never force it.
  visibility: nothing to evil.
ledger: `kind = TOLD, sourceId = "villageidiot", actorId = holder, targetIds = [target],
  shown = "GOOD"|"EVIL", verdict` per holder per night. With three VIs comparing notes publicly,
  this is the highest-value record in the game.
tests:
  - 3 VIs, no `Drunk` token -> a validation issue; one marked -> none; two marked -> "only one";
    a lone VI marked -> flagged.
  - 3 VI seats -> the step is per-holder with three `StepKey`s (today: one step, one picker).
  - seat 5 carries `villageidiot:Drunk` -> `holderId = 5` has a "DRUNK" caveat and `holderId = 2`
    has none (the engine is right today; the **UI's `firstOrNull()` is the bug**).
  - the `Drunk` reminder survives dawn and dusk and cannot be relocated by the tray.
  - the target saveable key includes `state.cycle` **and** the holder id, so a new cycle clears it.
  - `randomBag(extraVillageIdiots = 2)` produces 3 (today: structurally impossible).
open: is a Pit-Hag-created Village Idiot drunk or sober? Wiki says only "only one is created" —
  ask the ST and record the answer. **Boffin**: a Demon holding the Village Idiot ability needs
  the same `abilityHolders()` hook as the Pixie, and gets its own sub-panel on this row.
