# Lil' Monsta (lilmonsta) — exp demon

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Lil%27_Monsta> (fetched 2026-08-25; the
full Summary and How-to-Run sections were retrieved verbatim).

Current ability text (matches `characters.json`):

> "Each night, Minions choose who babysits Lil' Monsta & 'is the Demon'. Each night*, a
> player might die. [+1 Minion]"

Summary — **quoted verbatim**:

- "Lil' Monsta isn't a player, and is instead babysat by a Minion."
- "Each night, all Minions wake together and decide amongst themselves who babysits the
  Lil' Monsta. The Minions decide by pointing to a player, or otherwise make it obvious
  they have reached a decision. If they cannot reach a unanimous decision, the Storyteller
  decides."
- "If the Storyteller thinks it is funny, they may give this player the Lil' Monsta token,
  which they will need to hide in a pocket, under their hat, or somewhere appropriate.
  Players may not request that others empty their pockets."
- "The player with the Lil' Monsta token 'is the Demon'. **Good wins if they die.** They
  register as a Demon for characters like the Fortune Teller etc."
- "If a good player babysits Lil' Monsta, they 'are the Demon' but they remain good. A dead
  player babysitting Lil' Monsta ends the game because the Demon 'is dead'."
- "Minions babysitting Lil' Monsta keep their Minion ability."
- "Lil' Monsta isn't a player, so **can't be drunk or poisoned**."

How to Run — **quoted verbatim**:

> "During setup, remove Lil' Monsta and add a Minion token. On the first night, skip the
> **MINION INFO** and **DEMON INFO** steps.
>
> Each night, wake all Minions. The majority will (eventually) point to one player. If they
> can't decide, choose a player. (Give this player the Lil' Monsta token and wait for them
> to hide it, if you want!) Mark them with the **IS THE DEMON** reminder token. Put all
> Minions to sleep. Then, **if it is not the first night**, a player might **die** – mark
> them with a **DEAD** reminder and a shroud.
>
> The player marked **IS THE DEMON** registers as the Demon. If they die, declare that the
> game is over and good has won.
>
> You may need to kill a Minion or two at night using the Lil' Monsta's ability. On the
> final day, it would be unfair for the good team to have 2 or more Minions alive, since
> either Minion could have been chosen to babysit Lil' Monsta without any way for the good
> team to know which. Avoid this, and make the game fairer for the good team by killing
> Minions so that only 1 remains on the final day."

What the bag must look like:

- **Lil' Monsta is never in the bag and never occupies a seat.** "During setup, remove Lil'
  Monsta and add a Minion token."
- Reading the bracket together with that instruction: the **Demon slot becomes a Minion**,
  so for N players `demons = 0`, `minions = base.minions + 1`, and townsfolk/outsiders are
  unchanged. 10 players → 7 Townsfolk / 0 Outsiders / **3 Minions** / 0 Demons (total 10,
  evil count unchanged at 3).
- *Flagging the one genuine ambiguity:* the wiki never gives a worked bag. An alternative
  reading — apply "[+1 Minion]" as a normal townsfolk-for-minion trade *and then* replace
  the Demon with another Minion — would give 6/0/4/0 for 10 players (4 evil). That reading
  breaks down at low player counts (5 players → 2 Townsfolk vs 3 Minions) and leaves the
  bracket under-describing the bag, so the spec below uses the first reading. Worth a
  one-line confirmation with the user before implementing.
  Either way, **the app's current behaviour matches neither** — see below.

The first night in detail: the **MINION INFO** and **DEMON INFO** steps are skipped
entirely, so no demon bluffs are shown to anyone and the Minions do not learn each other at
those steps — they see each other when they all wake together at the Lil' Monsta step, and
the first babysitter is chosen there. Nobody dies on night 1.

Jinxes — **seven**, quoted from the wiki:

- **Hatter:** "If the Hatter dies & the Demon chooses Lil' Monsta, they also choose a Minion
  to become."
- **Magician:** "If the Magician is alive, the Storyteller chooses which Minion babysits
  Lil' Monsta."
- **Marionette:** "If there would be a Marionette in play, they enter play after the Demon &
  must start as their neighbor."
- **Poppy Grower:** "If Lil' Monsta & the Poppy Grower are alive, Minions wake one by one,
  until one of them chooses to take the Lil' Monsta token."
- **Psychopath:** "If the Psychopath is babysitting Lil' Monsta, they die when executed."
- **Scarlet Woman:** "If Lil' Monsta dies with 5 or more players alive, the Scarlet Woman
  babysits Lil' Monsta for the rest of the game."
- **Vizier:** "If the Vizier is babysitting Lil' Monsta, they die when executed."

Night order: `night_and_jinxes.json:319` (first night, after `MINION_INFO` at 309 and
`DEMON_INFO` at 313) and `night_and_jinxes.json:423` (other nights). Positions are correct.

## What the app does today

Data:

- `characters.json:2016` — ability text matches; `setup: true`;
  `remindersGlobal: ["Is The Demon", "Dead"]`; both night reminders describe the
  wake-minions → choose → show tokens flow.
- `night_guide.json:1587` — accurate, detailed prose for both nights, with a "To babysitter"
  token card ("YOU HOLD LIL' MONSTA - YOU ARE THE DEMON"). Prose only.
- `night_and_jinxes.json:110,120,125,130` — four jinxes (marionette, magician, poppygrower,
  scarletwoman), text reasonable. Missing: **Hatter, Psychopath, Vizier**.

Setup — **wrong in two independent ways**:

- `Setup.modifierFor` (`Setup.kt:121-232`) parses "[+1 Minion]" into `minionDelta = +1`
  only. `Distribution.plus` (`Setup.kt:21-32`) then trades that against the Townsfolk
  count. For 10 players the app demands **6 Townsfolk / 0 Outsiders / 3 Minions / 1 Demon**
  — i.e. it keeps a Demon slot *and* steals a Townsfolk.
- That Demon slot is filled by **Lil' Monsta itself**: `randomBag`
  (`GameActions.kt:338-402`) draws it as a normal Demon, and `GameActionsTest.kt:219-228`
  asserts exactly that (`assertEquals(1, bag.count { it.id == "lilmonsta" })`).
  `SetupTest.kt:64-71` locks in the distribution too. So a "correct" game per the app has a
  player who *is* Lil' Monsta — which the rules explicitly forbid.
- Consequence: if the storyteller builds the **correct** bag (no Lil' Monsta, one extra
  Minion, no Demon), `validateBag` (`GameActions.kt:456-478`) reports
  `"Demon: 0 in bag, expected 1"` and `validateSetupState` (`GameActions.kt:503-561`) makes
  the "Setup isn't legal yet" dialog fire at "Begin night" (`GameShell.kt:551-591`).

Night — the step **does not exist** in a correctly set-up game:

- `NightOrder.build` groups seats by `nightRoleId` and, at `NightOrder.kt:143-145`, does
  `if (holders.isEmpty() && !isFabledActive) continue`. With no seat holding `lilmonsta`,
  the Lil' Monsta row is silently dropped from both night sheets. The storyteller gets no
  step, no prose, no show card and no reminder tray for the central mechanic of the game.
- Conversely, in the app's own (incorrect) setup, a player holds `lilmonsta`, so:
  - `MINION_INFO` and `DEMON_INFO` both run (they must be skipped), the Demon-info row
    tells the ST to wake that player and nags "no bluffs chosen yet!"
    (`NightOrder.kt:81-119`);
  - the Lil' Monsta step's `QuickResolutions` falls through to `else ->`
    (`NightScreen.kt:518-523`) and renders `DemonKillPanel` — **including on the first
    night**, where nobody may die;
  - the "Demon is drunk/poisoned — the attack fails" banner (`NightScreen.kt:548-554`) can
    appear, though "Lil' Monsta isn't a player, so can't be drunk or poisoned".

Registration and endings — all keyed off a Demon-team **player**:

- `WinCheck.check` (`WinCheck.kt:21-22`) computes `demons` as players whose character team
  is `DEMON`. With no such player: `demons.isEmpty()`, so the "all Demons dead ⇒ good wins"
  branch never fires, and `alive.size <= 2 && aliveDemons.isNotEmpty()` never fires either.
  **A correct Lil' Monsta game can never reach any win advisory.**
- `InfoCalc` — Fortune Teller (`:325-342`), Clockmaker (`:218-241`), Sage (`:423-431`),
  Knight (`:433-440`), Flowergirl (`:307-323`) all look for `team == Team.DEMON` and will
  answer "NO" / "No Demon in the grimoire" for every question.
- `StatusEffects.deathNotes` (`:104-109`) only offers the Scarlet Woman note when a
  Demon-team character dies, so killing the babysitter produces no prompt at all.

Token handling:

- `Is The Demon` is a `remindersGlobal` label, so it appears in the night tray only when the
  Lil' Monsta step is expanded — which never happens in a correct game. It is reachable from
  a seat via `ReminderPicker`'s "Rest of script" section (`SeatSheet.kt:534-566`), but that
  path calls `viewModel.addReminder` (`SeatSheet.kt:113`), which is **non-exclusive** — so
  a storyteller moving the token nightly accumulates one on every babysitter.

Works today: the night-order positions, the jinx list (partially), and the night-guide prose
are all accurate — the character is well *described* and not at all *supported*.

## Defects and gaps

1. **P0 · The bag is built wrong: Lil' Monsta is dealt to a player.**
   `Setup.modifierFor` + `randomBag` put a Lil' Monsta character on a seat
   (`Setup.kt:121-232`, `GameActions.kt:338-402`, asserted in `GameActionsTest.kt:219-228`).
   The rules say "During setup, remove Lil' Monsta and add a Minion token."
   *Repro:* pick a Lil' Monsta script, press Randomize — a player is Lil' Monsta.

2. **P0 · The correct bag is rejected.**
   A bag with 0 Demons and `base.minions + 1` Minions fails `validateBag`
   ("Demon: 0 in bag, expected 1") and blocks "Deal randomly & start"
   (`SetupScreen.kt:485-488`), then trips the setup guard at "Begin night".

3. **P0 · The Townsfolk count is one too low.**
   `Distribution.plus` trades the +1 Minion against Townsfolk (`Setup.kt:21-32`), so the
   app asks for 6 Townsfolk at 10 players instead of 7. `SetupTest.kt:64-71` enshrines it.

4. **P0 · In a correct game the Lil' Monsta night step does not appear at all.**
   `NightOrder.kt:143-145` drops any character with no holder. The babysitter choice — an
   every-night, game-defining action — has no UI.

5. **P0 · Nothing registers as the Demon.**
   The `IS THE DEMON` holder must register as the Demon for the Fortune Teller, Clockmaker,
   Sage, Knight, Flowergirl, and for the win condition. Every one of those reads
   `team == Team.DEMON` (`InfoCalc.kt:220,314,328,424,434`; `WinCheck.kt:21-22`).
   *Repro:* correct Lil' Monsta game, Fortune Teller picks the babysitter → the app says
   NO.

6. **P0 · "Good wins if the babysitter dies" is never detected.**
   `WinCheck` has no branch; the ST must remember it. This is the game's only good-win
   condition.

7. **P0 · Evil's win condition is also undetectable.**
   `alive.size <= 2 && aliveDemons.isNotEmpty()` (`WinCheck.kt:88`) never fires with no
   Demon-team player.

8. **P1 · The Minion-info and Demon-info steps must be skipped and are not.**
   `NightOrder.kt:60-119` emits both whenever there are 7+ seats. In the app's own setup the
   ST is told to wake a Lil' Monsta "Demon" and is nagged for bluffs that are never shown.

9. **P1 · No babysitter-selection tool, and the token accumulates.**
   The nightly action is: wake all Minions, take a group decision (or decide yourself), mark
   `IS THE DEMON`, optionally hand over the physical token. None of it exists; the only way
   to place the token is a non-exclusive `addReminder` from a seat sheet
   (`SeatSheet.kt:109-116`).

10. **P1 · No "a player might die" tool that is separate from a demon's choice.**
    The nightly death is the **Storyteller's** choice (there is no Demon player choosing).
    The generic `DemonKillPanel` frames it as "who did <player> choose?" and gates on that
    player being alive (`NightScreen.kt:467`, `:518-523`).

11. **P1 · A kill is offered on the first night.**
    "Then, **if it is not the first night**, a player might die." The generic demon
    fallthrough shows the kill panel on night 1 too.

12. **P1 · The balance instruction is not surfaced.**
    "Kill Minions so that only 1 remains on the final day" is an explicit storyteller
    instruction with a countable trigger (2+ Minions alive as the game nears its end) and
    appears nowhere in the UI.

13. **P1 · The Scarlet Woman jinx is not wired to anything.**
    "If Lil' Monsta dies with 5 or more players alive, the Scarlet Woman babysits Lil'
    Monsta for the rest of the game." The app has the jinx text
    (`night_and_jinxes.json:130`) but the death of a babysitter produces no prompt
    (`StatusEffects.kt:104-109` requires a Demon-team character).

14. **P2 · Three jinxes missing: Hatter, Psychopath, Vizier.**
    The Psychopath and Vizier jinxes are day-time rules ("they die when executed" while
    babysitting) that the execution flow should warn about.

15. **P2 · The Magician and Poppy Grower jinxes change the procedure and are text-only.**
    Magician → the *Storyteller* picks the babysitter. Poppy Grower → Minions wake **one by
    one** until one takes the token. Both should reshape the step's UI, not sit in a jinx
    list.

16. **P2 · "Can't be drunk or poisoned" is not respected.**
    The impairment banner in `DemonKillPanel` (`NightScreen.kt:548-554`) can claim the
    attack fails because the babysitter is poisoned. The babysitter's own Minion ability can
    be poisoned; Lil' Monsta's cannot.

17. **P2 · The Marionette must not wake for the babysitter choice.**
    The app's own jinx text says so (`night_and_jinxes.json:110-113`) but nothing computes
    the "wake these Minions" list.

18. **P3 · The two enshrining tests must change.**
    `SetupTest.kt:64-71` and `GameActionsTest.kt:219-228` currently assert the wrong
    behaviour and will need rewriting alongside the fix.

## Proposed behaviour (spec)

### Setup

- Recognise Lil' Monsta as a **seatless character**. Concretely, add:

  ```kotlin
  // GameState
  val seatlessCharacterIds: List<String> = emptyList()   // e.g. ["lilmonsta"]
  ```

  set when a Lil' Monsta script is dealt (or toggled in the bag builder). This is the
  minimum general mechanism; the Kazali/Summoner "no demon in the bag" cases benefit too.
- `Setup.modifierFor("lilmonsta")` must yield `demonDelta = -1, minionDelta = +1,
  townsfolkRemoved = 0` so the adjusted distribution for N players is
  `townsfolk = base.townsfolk`, `outsiders = base.outsiders`,
  `minions = base.minions + 1`, `demons = 0`.
- `validateBag` must accept `demons == 0` when Lil' Monsta is the game's demon, and must
  **reject** any bag that contains a `lilmonsta` character token.
- `randomBag` must not draw Lil' Monsta into the bag; instead it draws the extra Minion.
- The bag-builder header should read: **"Lil' Monsta game: no Demon in the bag. Need
  <t> Townsfolk · <o> Outsiders · <m> Minions — Lil' Monsta is a token, not a player."**

### Night action (structured)

- **when:** **both** first and other nights, at the existing order positions
  (`night_and_jinxes.json:319` / `:423`). The step must be emitted because the *game* has
  Lil' Monsta, not because a seat holds it (`NightOrder.kt:143-145` must special-case
  seatless characters).
  Wake condition: at least one Minion exists (alive **or dead** — dead Minions still wake for
  this).
- **suppress:** `MINION_INFO` and `DEMON_INFO` on the first night whenever Lil' Monsta is in
  play, and stop demanding demon bluffs.
- **targets — part 1, the babysitter:** exactly 1 player.
  - Waking group (displayed, not chosen): every Minion **except the Marionette**, alive or
    dead. Poppy Grower jinx → the step instead says "wake Minions one by one until one takes
    the token" and lists them in seat order. Magician jinx → the step says "**you** choose
    which Minion babysits" and the group is not woken to decide.
  - Candidate constraints: any player may be given the token — normally a Minion, but a good
    player or a Traveller is legal. Default sort: alive Minions first, then the current
    holder, then everyone else. Same player two nights running is legal.
  - Warnings the picker must show inline: **"<name> is dead — if they babysit, the Demon is
    dead and good wins immediately."** and, for a good candidate, **"<name> is good — they
    stay good but 'are the Demon'."**
  - Effect: `placeExclusiveReminder(state, playerId, PlacedReminder("lilmonsta", "Is The
    Demon"))` — exclusive, so it *moves* rather than accumulating.
  - Show card: **"YOU HOLD LIL' MONSTA — YOU ARE THE DEMON"** with the Lil' Monsta token
    (already in `night_guide.json:1587`), plus a checkbox-style reminder **"Hand over the
    physical token and wait for them to hide it"**.
- **targets — part 2, the death (other nights only):** 0 or 1 player, alive.
  - Framed as the **Storyteller's** choice: *"A player might die — you choose, or nobody
    dies."* No "who did the Demon choose?" wording, no impairment banner about the Demon.
  - Effect: `kill(target, DeathCause.DEMON, lookup)` after showing
    `StatusEffects.deathNotes`; or an explicit **"Nobody dies tonight"** record.
  - Sorting hint: surface **"Minions alive: k. Kill Minions so only 1 remains on the final
    day."** and sort Minions first once `k >= 2` and the game is late.
  - On the **first night** this half of the panel must not render at all.
- **immediate effects:** as above. `lilmonsta:Dead` may be placed as a marker but the shroud
  (the `alive` flag) is the source of truth.
- **deferred effects:**
  - The `Is The Demon` mark persists **through the following day** — registration applies all
    day, and the Psychopath/Vizier jinxes are day-time rules.
  - If the `Is The Demon` holder dies at any time (night kill, execution, Slayer, anything),
    raise the win advisory **good wins** — unless the Scarlet Woman jinx applies (a living
    Scarlet Woman and 5+ players alive), in which case the app instead prompts **"Scarlet
    Woman takes Lil' Monsta"** and moves the token to her.
- **expiry:** `lilmonsta:Is The Demon` **never expires** — it is replaced each night by the
  exclusive placement. It must **not** be added to `EXPIRES_AT_DAWN`.
- **information:** none computed by Lil' Monsta itself; see Registration.
- **visibility:** the babysitter is shown the Lil' Monsta token and told they are the Demon.
  Minions see each other every night at this step (that is how they coordinate). **No demon
  bluffs are given** (both info steps are skipped).
- **day-time inputs the app must let the ST record:** nothing new, but the execution flow
  must warn when the executed player is the `Is The Demon` holder (good wins) and when a
  babysitting Psychopath or Vizier is executed (jinx: they die).

### Registration (shared with Legion — see `legion.md`)

`Registration.registersAs(state, lookup, player)` must return `{DEMON}` for the player
carrying `lilmonsta:Is The Demon`, in addition to whatever their own character registers as
(a babysitting Poisoner is both Minion and Demon). Consumers: Fortune Teller, Clockmaker,
Sage, Knight, Flowergirl, Undertaker's caveats, and `WinCheck`.

Note for the Undertaker: an executed babysitter still shows **their own character** token
(they are not Lil' Monsta), so the Undertaker calculator must not be changed — only add a
caveat line: *"<name> was holding Lil' Monsta."*

### Win check additions

- **Good wins** when the `Is The Demon` holder is dead (with the Scarlet Woman caution).
- **Good wins** when no player holds `Is The Demon` and no Minion is alive to take it
  (edge case: everyone who could babysit is dead).
- **Evil wins** when `alive.size <= 2` and the `Is The Demon` holder is alive.
- Caution list must mention the Scarlet Woman jinx whenever she is in play.

### UI text the step should display

- **"Wake all Minions together: <names>. They point to who babysits Lil' Monsta tonight."**
- **"If they can't agree, you choose."**
- **"Give <name> the Lil' Monsta token — wait for them to hide it."**
- **"<name> IS THE DEMON tonight and tomorrow. Good wins if they die."**
- Other nights: **"A player might die — your choice. Nobody dies is allowed."**
- Late game: **"3 Minions alive. Kill Minions so only 1 is left on the final day."**
- Magician jinx: **"Magician is alive — YOU choose the babysitter."**
- Poppy Grower jinx: **"Poppy Grower is alive — wake Minions one at a time until one takes
  the token."**

### Data changes

- `night_and_jinxes.json`: add the **Hatter**, **Psychopath** and **Vizier** jinxes.
- `night_guide.json:1587`: keep the prose; add a "Nobody dies on the first night" emphasis
  (already present) and a second show card for the physical-token handover.
- `characters.json:2016`: fine as is; `firstNightReminder` could add "Nobody dies tonight."
- New engine concept: `GameState.seatlessCharacterIds` (or an equivalent flag) plus
  `NightOrder` support for emitting steps for seatless characters.

## Tests to add

1. **Lil' Monsta is never in the bag.**
   *Given* a pool containing `lilmonsta`; *when* `randomBag(pool, 10, seed)`; *then* the bag
   contains no `lilmonsta`, 0 Demons and 3 Minions, and totals 10.
   (Replaces `GameActionsTest.kt:219-228`, which asserts the opposite.)

2. **Distribution keeps the Townsfolk count.**
   *Given* 10 players and Lil' Monsta as the game's demon; *then* the adjusted distribution
   is 7 Townsfolk / 0 Outsiders / 3 Minions / 0 Demons.
   (Replaces `SetupTest.kt:64-71`.)

3. **The correct bag validates; a bag containing Lil' Monsta does not.**
   *Given* 10 seats and a bag of 7 Townsfolk + 3 Minions with Lil' Monsta declared as the
   game's demon; *then* `validateBag` is empty. *Given* the same bag with a `lilmonsta`
   token added; *then* it reports an error.

4. **`validateSetupState` accepts a demon-less Lil' Monsta game.**
   So "Begin night" does not pop the setup guard.

5. **The night step exists with no seat holding Lil' Monsta.**
   *Given* a Lil' Monsta game; *then* both `firstNight` and `otherNight` step lists contain a
   `lilmonsta` step, and the first-night list contains **no** `MINION_INFO` or `DEMON_INFO`
   step.

6. **The babysitter token moves, never accumulates.**
   *Given* the token on player A; *when* it is placed on player B; *then* exactly one
   `lilmonsta:Is The Demon` reminder exists in the whole grimoire, on B.

7. **The babysitter registers as the Demon.**
   *Given* a Fortune Teller choosing the babysitter and one other player; *then* the result
   is YES. *And* the Clockmaker computes a real Demon→Minion distance. *And* the Sage and
   Knight name the babysitter.

8. **Good wins when the babysitter dies.**
   *Given* the babysitter is executed; *then* `WinCheck` returns `goodWins = true` with a
   Lil' Monsta reason.

9. **Scarlet Woman jinx overrides that win.**
   *Given* a living Scarlet Woman and 5+ players alive when the babysitter dies; *then* the
   advisory is a "Scarlet Woman takes Lil' Monsta" prompt rather than a good win, and after
   confirming, the token sits on the Scarlet Woman.

10. **Evil wins at two alive.**
    *Given* 2 players alive, one of whom holds `Is The Demon`; *then*
    `goodWins = false`.

11. **No death on the first night.**
    *Given* the first night; *then* the Lil' Monsta step exposes the babysitter picker but no
    kill action.

12. **A good babysitter stays good.**
    *Given* the token placed on a Townsfolk; *then* `player.isEvil(lookup)` is still false,
    while `Registration.registersAs` includes `DEMON`.

13. **The Marionette is not in the waking group.**
    *Given* a Marionette and two other Minions; *then* the step's "wake these Minions" list
    contains only the two others.

14. **Lil' Monsta cannot be poisoned.**
    *Given* the babysitter is marked `Poisoned`; *then* the night step shows no
    "the attack fails" banner and the kill still resolves normally.
