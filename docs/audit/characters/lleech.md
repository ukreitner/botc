# Lleech (lleech) — Experimental Demon

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Lleech> (fetched 2026-08-25),
jinx list <https://wiki.bloodontheclocktower.com/Djinn>, rulings index
<https://botc.me/character/lleech>.

**Current ability text (verbatim):**
> "Each night\*, choose a player: they die. You start by choosing a player: they are poisoned. You die if & only if they are dead."

**How to Run (verbatim):**
> "During the first night, wake the Lleech. They point at any player. That player is poisoned—mark them with the POISONED reminder. Put the Lleech to sleep.
>
> Each night except the first, wake the Lleech. They point at any player. That player dies—mark them with the DEAD reminder. Put the Lleech to sleep.
>
> If the Lleech would die but the player marked with the Lleech's POISONED reminder is alive, the Lleech does not die. If the player marked with the Lleech's POISONED reminder is dead, the Lleech dies and the good team wins."

**Examples (verbatim):**
> "The Lleech poisons the Noble. The Noble learns false information. The Lleech is executed, but does not die. The next day, the Noble is executed. The Noble and the Lleech die. Good wins.
>
> The Lleech poisons the Farmer. The Lleech is made drunk by the Courtier. The poisoned Farmer dies, and the game continues because the Lleech is also drunk. The drunk Lleech is executed and dies, and good wins."

**Rules that follow from the above and from the rulings index:**

- The host is chosen on **night 1 only**. There is no re-choice on later nights; the
  host token never moves. The Lleech's night-1 action is **not a kill**.
- The life-link is **bidirectional and absolute**: while the host lives, *nothing*
  kills the Lleech (execution, Slayer, Vigormortis, Witch, Gossip, Godfather,
  Tinker, Assassin, Psychopath, Gambler, its own self-choice…). When the host
  dies **by any means, at any time (day or night)**, the Lleech dies **immediately**
  — not at dawn, not at the Lleech's night-order position.
- The life-link is an *ability*, so a **drunk or poisoned Lleech loses it**
  (Example 2): a drunk Lleech dies normally to execution and does **not** die when
  its host dies.
- The Lleech **may choose itself** as its host: "If Lleech poisons themself, they
  are permanently poisoned" (botc.me). A self-hosted Lleech is therefore
  permanently drunk-equivalent: it has no ability at all — no nightly kill, and no
  life-link (it dies normally).
- **Soldier as host:** "Soldier chosen by the Lleech as a host is not poisoned but
  becomes the host" (botc.me). So *host* and *poisoned* are separate facts; the
  Soldier is immune to the poison but still gates the Lleech's life.
- The Lleech may kill its own host on a later night; that kills the Lleech too.
- Killing the Lleech's host is the good team's win condition — the wiki states
  outright "the Lleech dies and the good team wins."

**Jinxes (verbatim, from the Djinn page):**
- Heretic / Lleech: "Only 1 jinxed character can be in play."
- Lleech / Mastermind: "If the Mastermind is alive and the Lleech host dies by
  execution, the Lleech lives but loses their ability."
- Lleech / Slayer: "If the Slayer slays the Lleech host, the host dies."
- (botc.me also records a Goon interaction: a Goon host is not poisoned, turns evil
  and makes the Lleech **drunk until dusk**; a Goon executed that first day
  therefore does **not** kill the Lleech, because the drunk Lleech has no life-link.)

**Uncertain / not covered by the wiki (flagged, not guessed):**
- What happens to the host's poison after the Lleech dies. The repo's jinx text
  (`night_and_jinxes.json:79`) asserts "If the Lleech has poisoned the Heretic, and
  the Lleech dies, the Heretic remains poisoned" — that is **not** the current
  official Heretic/Lleech jinx, which reads "Only 1 jinxed character can be in
  play." Treat the repo text as stale (see D9).
- Whether a Scarlet Woman who becomes the Lleech inherits the old host. Not
  addressed anywhere I could find; spec below prompts the ST rather than automating.

## What the app does today

**Data**
- `engine/src/main/resources/botc/data/characters.json:2031-2044` — ability text at
  `:2035` matches the wiki exactly. `firstNightReminder` (`:2037`) and
  `otherNightReminder` (`:2038`) are **the same string**, "The Lleech chooses a
  player.", so the night sheet gives no hint that night 1 is a poison and night 2+
  is a kill. Reminders are `["Dead", "Poisoned"]` — no *Host* token.
- `night_and_jinxes.json` — first night order index 25 (`:320`), other night index 49
  (`:422`). Both positions look right. Jinxes present: lleech/heretic (`:79`),
  lleech/slayer (`:179`). **Missing: lleech/mastermind.**
- `night_guide.json:1611-1627` — good prose for both nights, plus a "Choose host"
  message card. Two problems: it asserts the host is "poisoned for as long as the
  Lleech lives" (no such rule; the poison is not tied to the Lleech's life in the
  official text), and it never mentions that a drunk/poisoned Lleech loses the link.

**Night 1 (the storyteller's actual experience)**
- The step renders from `NightOrder.build` (`NightOrder.kt:142-178`) with the detail
  "The Lleech chooses a player."
- `StepDetailPanel` (`NightScreen.kt:770-934`) shows the guide prose, then calls
  `QuickResolutions` (`NightScreen.kt:462-525`). Lleech has no case, so it falls to
  the `else` branch (`:518-523`): `character.team == Team.DEMON && holder.alive`
  → **`DemonKillPanel` is rendered on the first night**, headed "Demon kill — who did
  <name> choose?" with a red "<name> dies" button (`NightScreen.kt:543-547`,
  `:624-635`). This is the Pukka defect the user reported, verbatim: the app offers a
  kill on a night when the ability poisons.
- To place the poison the ST must notice the bottom `NightToolTray`, tap the
  "Poisoned" chip, then tap the seat (`NightScreen.kt:283-354`). Because
  `allReminders` has one copy, it goes through `placeExclusiveReminder`
  (`GameActions.kt:194-201`) — correct behaviour, but entirely manual.
- The poison then works for info characters, because `StatusEffects.isImpaired`
  (`StatusEffects.kt:36-46`) matches any reminder label containing "poison".
- `("lleech","Poisoned")` is in neither `EXPIRES_AT_DAWN` nor `EXPIRES_AT_DUSK`
  (`GameActions.kt:218-242`), so the token correctly persists. **Works.**

**Nights 2+**
- `DemonKillPanel` again — correct modality this time. It lists every seat including
  dead ones and the Lleech itself (`NightScreen.kt:559-583`), shows
  `StatusEffects.deathNotes` for the target (`:588`), and kills with
  `DeathCause.DEMON` (`:629`).

**Death handling**
- `StatusEffects.deathNotes` (`StatusEffects.kt:78`) emits one unconditional line on
  the *Lleech's own* seat: "The Lleech only dies if its poisoned host is dead."
- Nothing at all is attached to the **host's** seat, and nothing happens when the
  host dies.
- `GameActions.kill` (`:136-156`) has no interception hook: any kill goes through.
- `WinCheck.check` (`WinCheck.kt:70-86`) will announce "Every Demon is dead — good
  wins" if the ST mistakenly kills the Lleech while the host lives, with no Lleech
  caution in the caution list (`:71-80` covers only Scarlet Woman, Mastermind, Imp).

## Defects and gaps

1. **P0 · Night 1 offers a kill instead of a host choice.**
   Rules: night 1 the Lleech poisons; the app shows "Demon kill — who did X choose?"
   and a "<name> dies" button. `NightScreen.kt:518-523` → `:534-638`. Repro: start a
   game with a Lleech, open the Night tab, expand the Lleech step. Identical in cause
   to the reported Pukka bug (`QuickResolutions` has no per-Demon night-1 branch).

2. **P0 · The host's death does not kill the Lleech.**
   Rules: "If the player marked with the Lleech's POISONED reminder is dead, the
   Lleech dies and the good team wins." The app does nothing. Repro: place the
   Lleech's Poisoned token on Alice, then execute Alice from `DayScreen.kt:111-114`
   — Alice dies, the Lleech stays alive, no prompt, no advisory, the game silently
   continues past its actual ending. `GameActions.kill:136-156` has no post-kill
   trigger; `StatusEffects.deathNotes` says nothing on the host's seat.

3. **P0 · The Lleech can be killed while its host is alive.**
   Rules: it cannot die. The app's execute button (`DayScreen.kt:111-114`,
   `:350-357`), the seat sheet kill buttons (`SeatSheet.kt:267-300`) and the
   `DemonKillPanel` confirm button all kill unconditionally. The single advisory line
   (`StatusEffects.kt:78`) is easy to miss and is not a block. Repro: execute the
   Lleech on day 1 while its host lives — the Lleech dies and `WinCheck` declares
   good the winner.

4. **P0 · The "only dies if its host is dead" note is wrong in three states.**
   `StatusEffects.kt:78` prints unconditionally, so it also prints (a) when the host
   is already dead — when the Lleech *does* die; (b) when the Lleech is drunk or
   poisoned — when the link is off and the Lleech *does* die (wiki Example 2); (c)
   when the Lleech is its own host. It never names the host. Repro: kill the host,
   then open the Lleech's seat sheet — it still says the Lleech is safe.

5. **P1 · No Host concept; poison and host-ness are conflated.**
   The official Soldier ruling ("not poisoned but becomes the host") cannot be
   represented: the app's only marker is a token whose label contains "poison", and
   `isImpaired` keys off exactly that substring (`StatusEffects.kt:38-44`). A
   Soldier host is therefore either wrongly poisoned or invisible as the host.
   `characters.json:2039-2042` has no Host reminder.

6. **P1 · A drunk/poisoned Lleech is not modelled.**
   Neither the life-link nor its suspension is derived. `DemonKillPanel:548-554`
   does warn the ST that an impaired Demon's attack fails, which is right for the
   nightly kill, but the far more consequential effect — the Lleech becomes killable
   and stops dying with its host — is nowhere.

7. **P1 · Self-host is not handled.**
   The Lleech may pick itself; it is then permanently poisoned and has no ability at
   all. The app would let the ST place `lleech:Poisoned` on the Lleech's own seat,
   which `isImpaired` then reports, but nothing suppresses the nightly kill panel or
   the (now absent) life-link.

8. **P1 · Missing Mastermind jinx and no Mastermind branch on host death.**
   `night_and_jinxes.json` has no lleech/mastermind entry, so the "Jinxes in play"
   dialog (`GameExtras.kt:202-231`) will not surface it. The rule needs its own
   branch: host dies **by execution** with a living Mastermind → the Lleech lives but
   loses its ability.

9. **P1 · Wrong Heretic jinx text in the data.**
   `night_and_jinxes.json:79-82` says "If the Lleech has poisoned the Heretic, and
   the Lleech dies, the Heretic remains poisoned." The current official text is
   "Only 1 jinxed character can be in play." The stale text also encodes a poison
   -duration rule that is not in the current rules.

10. **P1 · No win advisory for the Lleech.**
    `WinCheck.kt:71-80` lists Scarlet Woman / Mastermind / Imp cautions but none for
    the Lleech, and there is no positive advisory when the host dies ("good wins").

11. **P2 · The night sheet's first/other text is identical.**
    `characters.json:2037-2038` both read "The Lleech chooses a player." The official
    first-night reminder is "The Lleech points to a player. Place the Poisoned
    reminder token."

12. **P2 · Guide prose asserts an unsupported rule.**
    `night_guide.json:1613` — "poisoned for as long as the Lleech lives".

13. **P2 · Holder resolution by first seat index.**
    `NightScreen.kt:467` (`step.playerIds.firstOrNull()`) picks the lowest-seat
    holder, and `:520` requires `holder.alive`. If a second seat ever carries
    `characterId == "lleech"` — which `GameActions.starPass` (`:79-96`) produces for
    the Imp/Fang Gu family by leaving the dead demon's `characterId` intact, and
    which a manual Pit-Hag/Kazali/Scarlet-Woman character change can also produce —
    the wrong seat drives the panel, and if that seat is dead the Lleech's step
    silently offers no tools at all. Same root cause as the cross-cutting star-pass
    defect.

## Proposed behaviour (spec)

### State additions

Two new reminder tokens on `lleech` (`characters.json` `reminders`):
`"Host"` and keep `"Poisoned"`; keep `"Dead"`.

- `lleech:Host` — **exclusive, never expires**. Defines the life-link. Placed on
  night 1 and never moved.
- `lleech:Poisoned` — **exclusive, never expires**. Placed with the Host token by
  default; omitted when the host is a Soldier (or is otherwise ruled immune, e.g.
  Goon), so `isImpaired` stays honest.
- `lleech:No ability` — placed only by the Mastermind jinx branch.

### Night action — structured form

**First night**
- **when:** first night; wake condition: the Lleech seat is alive and does not
  already hold a `lleech:Host` link anywhere in the grimoire.
- **targets:** exactly 1 player. Constraints: any seat, alive or dead, **including
  the Lleech itself**. Picker defaults: alive players first, then the Lleech's own
  seat last but *present and labelled "(self — becomes permanently poisoned, loses
  all ability)"*. Travellers allowed.
- **immediate effects:** place `lleech:Host` (exclusive) on the target. Place
  `lleech:Poisoned` (exclusive) on the same target **unless** the ST unticks the
  offered checkbox "Host is immune to the poison (Soldier / Goon)". If the target is
  the Lleech itself, place both on the Lleech and additionally show the standing
  banner "Self-hosted: this Lleech is permanently poisoned — it has no kill and no
  life-link."
  If the target is a **Goon**, additionally offer one tap that places
  `goon:Drunk` on the Lleech (expires at dusk) and flips the Goon evil, per the
  Goon's own rules.
- **no kill is offered on this night** — remove the `DemonKillPanel` from the
  first-night path entirely.
- **deferred effects:** see "Life-link engine" below.
- **expiry:** never.
- **information:** none shown to the Lleech beyond the "CHOOSE YOUR HOST…" card
  already in `night_guide.json`.
- **visibility:** the Minions are not told who the host is. Nothing extra to show.
- **UI text:** *"Night 1 — the Lleech does NOT kill. They point at a player: that
  player becomes the host and is poisoned. The Lleech cannot die while the host
  lives."*

**Other nights**
- **when:** other nights; wake condition: Lleech alive **and** not marked
  `exorcist:Chosen` **and** not impaired-with-no-kill (still wake, but warn).
- **targets:** 1 player, alive, any seat including the host and including itself.
- **immediate effects:** standard demon kill via `GameActions.kill(..., DEMON)`
  after the usual `deathNotes` protection review. If the chosen target **is the
  host**, the panel must lead with, in red: *"That is the Lleech's host — killing
  them kills the Lleech too and ends the game for evil."* and the confirm button
  becomes "<host> dies » the Lleech dies » good wins".
- **impaired:** existing `DemonKillPanel:548-554` warning is correct; additionally,
  a self-hosted Lleech is permanently impaired, so the panel should say "no kill
  tonight (self-hosted)" and offer only "No kill".
- **expiry:** none.

### Life-link engine (the part that must be automatic)

Implement as a pure engine function used by every kill path, not as UI text:

```
LleechLink.state(state, lookup) -> LleechLink?   // seat of the Lleech, seat of the host
LleechLink.blocksDeathOf(state, lookup, playerId) : String?   // reason, or null
LleechLink.consequencesOf(state, lookup, deadPlayerId) : List<Consequence>
```

- `blocksDeathOf(lleechSeat)` returns a reason **iff** the host exists, the host is
  alive, and the Lleech is **not** impaired (`StatusEffects.isImpaired`). Wire it
  into `GameActions.kill` as a hard guard that returns the state unchanged plus a
  surfaced message, and into every UI kill entry point
  (`DayScreen.kt:111-114`, `DayScreen.kt:350-357`, `SeatSheet.kt:267-300`,
  `NightScreen.kt:624-635`) so the buttons read "Cannot die — host alive" instead
  of killing.
- `consequencesOf(host)` — when the seat holding `lleech:Host` transitions to dead,
  **immediately** (same action, same undo step):
  - if the Lleech is impaired → nothing happens (Example 2); surface
    "The Lleech is drunk/poisoned — it survives its host's death."
  - else if a **Mastermind is alive** and the host's `DeathCause == EXECUTION` →
    the Lleech lives, place `lleech:No ability` on the Lleech, surface the jinx text.
  - else → kill the Lleech with a new `DeathCause.OTHER_NIGHT_DEATH` (day deaths
    reuse `DeathCause.STORYTELLER`) and raise a win advisory
    "The Lleech's host is dead — the Lleech dies and the good team wins."
  This must fire for **all** host deaths: execution, exile, demon kill, Slayer,
  Gossip, Godfather, Assassin, Tinker, storyteller kill.
- `StatusEffects.deathNotes` (`:78`) becomes conditional and names the host:
  - on the **Lleech's** seat, host alive & Lleech sober → "Cannot die: the host
    (<Name>) is alive."
  - on the **Lleech's** seat, host dead or Lleech impaired → "The life-link is off —
    this Lleech dies normally."
  - on the **host's** seat → "This is the Lleech's host: killing them kills the
    Lleech (<Name>) and good wins." (plus the Mastermind branch when applicable).
- `WinCheck.check` gains: (a) a positive advisory when the host is dead and the
  Lleech is dead; (b) a caution on the demons-dead advisory when a `lleech:Host`
  token sits on a living player: "A Lleech cannot be dead while its host lives —
  check that death."

### Day-time inputs
None required. The Lleech creates no day-time claims to record.

### Interactions/jinxes to handle explicitly
- **Slayer**: shooting the Lleech does nothing while the host lives; shooting the
  **host** kills the host (and hence the Lleech). The Slayer resolution UI must
  offer "Slayer shot the Lleech's host" as a distinct outcome.
- **Mastermind**: branch above.
- **Heretic**: replace the repo jinx text with "Only 1 jinxed character can be in
  play." and enforce it in `validateBag` (reject a bag containing both).
- **Exorcist**: choosing the Lleech blocks the nightly kill only; it does not touch
  the life-link.
- **Scarlet Woman**: when the Lleech dies with 5+ alive, prompt "Does the new Lleech
  inherit the host?" and let the ST choose — the rule is not documented; do **not**
  auto-transfer, and record the ST's answer in the log.
- **Goon** as host: as above.
- **Vortox / Vigormortis / Fang Gu**: no special rule; the life-link guard covers
  them because it sits in `kill`.

### Data changes
- `characters.json:2037` → `"The Lleech points to a player. Place the Poisoned
  reminder token."`; add `"Host"` to `reminders` (`:2039-2042`).
- `night_and_jinxes.json:79-82` → Heretic text corrected; add
  `{"id1":"lleech","id2":"mastermind","reason":"If the Mastermind is alive and the
  Lleech host dies by execution, the Lleech lives but loses their ability."}`.
- `night_guide.json:1613` → drop "for as long as the Lleech lives"; add "A drunk or
  poisoned Lleech loses the life-link: it can be killed, and it survives its host's
  death."; add to the `other` entry "The Lleech may kill its own host — that kills
  the Lleech too."

## Tests to add

1. `Given` a Lleech with `lleech:Host` on a living Noble, `When`
   `GameActions.kill(lleech, EXECUTION)`, `Then` the Lleech is still alive and the
   returned state is unchanged apart from a surfaced block reason.
2. `Given` the same setup, `When` the Noble is executed, `Then` the Noble is dead
   **and** the Lleech is dead in the same resulting state, and `WinCheck.check`
   returns `goodWins = true` citing the Lleech.
3. `Given` a Lleech marked `courtier:Drunk` and a living host, `When` the Lleech is
   executed, `Then` the Lleech dies (Example 2).
4. `Given` a Lleech marked `courtier:Drunk`, `When` the host is killed, `Then` the
   Lleech is still alive.
5. `Given` a living Mastermind and a `lleech:Host` on Alice, `When` Alice is
   executed, `Then` the Lleech is alive and holds `lleech:No ability`.
6. `Given` the same but Alice dies by `DeathCause.DEMON`, `Then` the Lleech dies
   (the Mastermind jinx is execution-only).
7. `Given` a Lleech that hosted itself, `Then` `StatusEffects.isImpaired` is true for
   the Lleech and the life-link does not block its execution.
8. `Given` a Soldier host marked `lleech:Host` **without** `lleech:Poisoned`,
   `Then` `isImpaired(soldier)` is false and the life-link still blocks the Lleech's
   death.
9. `Given` a first-night state, `Then` the Lleech's night step exposes a host picker
   and **no** kill action (assert on the step model, not on Compose).
10. `Given` `validateBag` with both `lleech` and `heretic`, `Then` an issue is
    reported (Heretic jinx).
