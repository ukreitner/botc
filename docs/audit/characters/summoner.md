# Summoner (summoner) — exp (Carousel) minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Summoner>

Current ability text:

> "You get 3 bluffs. On the 3rd night, choose a player: they become an evil
> Demon of your choice. [No Demon]"

`characters.json:1877` matches this text exactly — **no drift**.

### How to run (wiki, verbatim, in order)

- "During the setup phase, remove the Demon and add a Townsfolk."
- "When preparing the first night, put the Summoner's **NIGHT 1** reminder by the Summoner."
- "When preparing the second night, put the Summoner's **NIGHT 2** reminder by the Summoner."
- "When preparing the third night, put the Summoner's **NIGHT 3** reminder by the Summoner."
- "During the first night, show the Summoner 3 not-in-play characters as bluffs."
- "During the night, if the Summoner has a **NIGHT 3** reminder, wake the Summoner."
- "They point at a player, and to a Demon icon on the character sheet."
- "Put the Summoner to sleep."
- "Wake the chosen player."
- "Show the **YOU ARE** info token, then the Demon token."
- "Show the **YOU ARE** info token, then give a thumbs down."
- "Replace their character token with the Demon token and put the new Demon to sleep."

### Key clarifications (verbatim)

- "The Summoner may choose any player to become the Demon, even themselves."
- "The newly created Demon acts on the same night that it is created."
- "The new Demon does not learn which players are Minions, or vice versa."
- "if the Summoner becomes unable to create a Demon (due to dying, becoming drunk on night 3 etc.) good wins."
- Example: "On the third night, the Summoner chooses the Snitch player, and the Lleech. The Snitch becomes the evil Lleech, and chooses a player to poison, and a player to kill."
- Example: "On the first day, the Summoner is executed. Good wins."
- Example: "On the third night, the Summoner turns the Alchemist into the Leviathan. At dawn, all players learn that Leviathan is in play, and that it is day three of five."

### Jinxes (wiki — all 14)

| Partner | Text |
|---|---|
| Alchemist | "The Alchemist-Summoner does not get bluffs, and chooses which Demon but not which player." |
| Clockmaker | "The Summoner registers as the Demon to the Clockmaker." |
| Courtier | "If the living Summoner has no ability, the Storyteller has the Summoner ability." |
| Engineer | "If the living Summoner is removed from play, the Storyteller has the Summoner ability." |
| Hatter | "If the Summoner creates a second living Demon, deaths tonight are arbitrary." |
| Kazali | "If the Summoner creates a second living Demon, deaths tonight are arbitrary." |
| Legion | "If Legion is summoned, all evil players become Legion." |
| Lord of Typhon | "If a Lord of Typhon is summoned, they must neighbor a Minion & their other neighbor becomes an evil Minion." |
| Marionette | "If there would be a Marionette in play, they enter play after the Demon & must start as their neighbor." |
| Pit-Hag | "If the Summoner creates a second living Demon, deaths tonight are arbitrary." |
| Poppy Grower | "If the Poppy Grower is alive on the 3rd night, the Summoner chooses which Demon but not which player." |
| Preacher | "If the living Summoner has no ability, the Storyteller has the Summoner ability." |
| Pukka | "The Summoner may summon a Pukka on the 2nd night instead of the 3rd." |
| Zombuul | "If the Summoner summons a dead player into the Zombuul, the Zombuul has already 'died once'." |

Note the Courtier/Preacher/Poisoner interaction is subtler than the app's data records: the wiki's Courtier jinx is "**the Storyteller has the Summoner ability**" (i.e. the Storyteller picks both player and Demon), whereas the app's data (see below) says the Summoner picks the Demon and the Storyteller picks the player. I could not reconcile these two from a single page; the Poisoner jinx in the app's data ("the Summoner chooses which Demon, but the Storyteller chooses which player becomes that Demon") is not listed at all on the wiki's Summoner jinx table, which lists Courtier and Preacher with the "Storyteller has the Summoner ability" wording instead. **Flagging as an unresolved rule question for the lead** — but either way, the app must model "the Summoner is impaired on night 3" as *something still happens*, not as *nothing happens*, which is what the app's night guide currently says.

## What the app does today

Data paths:
- `characters.json:1877` — text, `setup: true`, reminders `["Night 1","Night 2","Night 3"]`, first/other night reminders matching the official wording.
- `night_and_jinxes.json:312` (firstNight index 17, between `lunatic` and `DEMON_INFO`) and `:403` (otherNight index 30, between `scarletwoman` and `lunatic`). Both positions are correct.
- `night_and_jinxes.json:114` marionette jinx, `:234` alchemist, `:239` poisoner, `:244` courtier. **Only 4 of the 14 official jinxes are present**, and the Marionette one carries stale text (see defects).
- `night_guide.json:1443` — first/other prose. The `other` prose ends with "If the Summoner is dead or drunk/poisoned on night 3, no Demon is created (or you choose, per your ruling) — by default evil loses without a Demon."

Engine:
- `Setup.modifierFor` (`Setup.kt:131-133`) special-cases `[No Demon]` → `demonDelta = -1`, which `Distribution.plus` (`Setup.kt:21-32`) converts into "one fewer Demon, one more Townsfolk". Covered by `SetupTest.kt:97` and `GameActionsTest.kt:231` ("random bag with summoner has no demon"). **This works.**
- `NightOrder.build` (`NightOrder.kt:40-209`) renders the Summoner step from `characters.json`'s night reminder text.
- `QuickResolutions` (`NightScreen.kt:462-525`) has no `"summoner"` case; the Summoner is a Minion so it never reaches `DemonKillPanel` either.
- `WinCheck.check` (`WinCheck.kt:70-86`) only fires "good wins" when `demons.isNotEmpty() && aliveDemons.isEmpty()`.

Storyteller experience:
1. Setup: the bag builder produces 0 Demons. Good.
2. Night 1: the Summoner's step says "Show the 'These characters are not in play' token. Show 3 not-in-play good character tokens." The guide adds the right prose. **But there is no way to show the bluffs from this step** — the `ShowCard.BluffsCard` chip is gated on `step.id == NightMarkers.DEMON_INFO` (`NightScreen.kt:783-788`). The Summoner's own show card is a plain message card (`night_guide.json:1443`).
3. Night 1 also renders **Minion info** and **Demon info** steps whenever there are 7+ seats (`NightOrder.kt:60-119`). With no Demon in play, the Minion-info detail reads "Wake all Minions (…). They see each other, then point out the Demon." with an empty parenthesis, and the Demon-info step reads "Wake the Demon . Point out the Minions (…), then show 3 not-in-play good characters as bluffs: …" — instructing the Storyteller to wake a Demon that does not exist.
4. Night reminders: the `Night 1/2/3` tokens must be placed and re-placed entirely by hand through the tray, every night. Nothing in the codebase mentions them (`grep -rn '"Night 1"'` matches nothing in `engine/src` or `app/src`).
5. Night 3: the step text tells the Storyteller what to do; there is no tool. Turning the target into a Demon requires: Grimoire tab → target's seat → "Change character" → pick a Demon → "Flip alignment" if the target was good → clear the shown identity → then find the new Demon's own night step further down the sheet and run it. Nothing prompts any of that.
6. Day 1–2: if the Summoner is executed on day 1, the app says nothing (there are no Demons at all, so `WinCheck` returns null). The Storyteller has to know that good has already won.

## Defects and gaps

1. **P0 · A Marionette + Summoner game cannot be started** — `GameActions.validateSetupState` (`GameActions.kt:540-543`) requires "the Marionette must neighbor the Demon"; in a Summoner game there is no Demon, so the check always fails and `GameShell.requestPhaseAdvance` (`GameShell.kt:133-140`) blocks the first night with an unsatisfiable error. The official jinx is "If there would be a Marionette in play, they enter play after the Demon & must start as their neighbor" — i.e. the Marionette does not enter play until night 3. Repro: assign a Summoner and a Marionette, press "Begin night".
2. **P0 · Night 1 tells the Storyteller to wake a non-existent Demon** — `NightOrder.kt:81-119` emits the DEMON_INFO step unconditionally for 7+ seats, producing "Wake the Demon ." and offering the demon bluffs to nobody, and `NightOrder.kt:60-80` emits "…then point out the Demon" with no Demon. Under the Summoner the Minions see each other but learn no Demon, and the 3 bluffs go to the **Summoner** instead. Repro: 8-player game with a Summoner, open the Night tab on night 1.
3. **P0 · Nothing detects "evil has no Demon and can no longer get one" ⇒ good wins** — the wiki is explicit: "if the Summoner becomes unable to create a Demon (due to dying, becoming drunk on night 3 etc.) good wins", and the example "On the first day, the Summoner is executed. Good wins." `WinCheck.check` (`WinCheck.kt:70-86`) requires `demons.isNotEmpty()` to fire at all, so a Summoner game with a dead Summoner and no Demon produces **no advisory whatsoever**. Repro: Summoner game, execute the Summoner on day 1 — no win dialog.
4. **P1 · No night-3 resolver** — the highest-consequence single action in the character (create a Demon: change character, flip alignment, clear shown identity, show the YOU ARE + Demon tokens + thumbs-down) is four separate manual operations across two screens. `NightScreen.kt:462-525` has cases for `snakecharmer`, `fanggu` and `professor` but none for `summoner`.
5. **P1 · The `Night 1/2/3` reminders are 100% manual** — the whole ability is a three-night countdown and the app never places, advances or removes the token. `characters.json:1877` defines the tokens; nothing uses them. The same defect afflicts the Xaan (see `xaan.md`) — a shared "night counter token" mechanism would fix both.
6. **P1 · The Summoner's 3 bluffs are not wired to the bluffs feature** — `state.demonBluffIds` exists and `BluffsSheet` (`BluffsSheet.kt`) picks them, but the "Show bluffs full-screen" chip only appears on the DEMON_INFO step (`NightScreen.kt:783-788`). On the Summoner step the Storyteller gets a plain editable message card. Also, the bluffs menu is labelled "Demon bluffs" everywhere (`GameShell.kt:219`, `BluffsSheet.kt:56`), which is wrong in a Summoner game.
7. **P1 · The night guide's ruling on an impaired Summoner is wrong or at best incomplete** — `night_guide.json:1443` says "If the Summoner is dead or drunk/poisoned on night 3, no Demon is created (or you choose, per your ruling) — by default evil loses without a Demon." The app's own jinx data (`night_and_jinxes.json:239`, `:244`) says the opposite for the Poisoner and Courtier: a Demon **is** created, with the Storyteller choosing the player. Dead vs. drunk are different cases and the guide conflates them.
8. **P1 · The new Demon acts the same night, and nothing says so at the moment it matters** — `NightOrder` recomputes from `state.players` (`NightScreen.kt:83-90`), and every Demon sits at otherNight index 36–54, i.e. *after* the Summoner at index 30, so the new step does appear. But the Storyteller is not told to expect it, and the wiki's "The new Demon does not learn which players are Minions, or vice versa" is nowhere in the app.
9. **P1 · 10 of 14 official jinxes are missing** from `night_and_jinxes.json`: Clockmaker, Engineer, Hatter, Kazali, Legion, Lord of Typhon, Pit-Hag, Poppy Grower, Pukka, Zombuul. Several are mechanical, not flavour: Poppy Grower ("Summoner chooses which Demon but not which player"), Pukka ("may summon on the 2nd night instead of the 3rd" — changes the night the step fires), Legion ("all evil players become Legion"), Lord of Typhon (forces a seating change and creates a new Minion).
10. **P2 · The Marionette jinx text in the dataset is stale** — `night_and_jinxes.json:114` reads "The Marionette neighbors the Summoner, not the Demon. The Summoner knows who the Marionette is."; the current wiki text is "If there would be a Marionette in play, they enter play after the Demon & must start as their neighbor."
11. **P2 · Dawn after the summoning is silent** — the Leviathan example requires a public announcement at dawn ("all players learn that Leviathan is in play, and that it is day three of five"); more generally the town's whole picture changes the morning after night 3 and the app has no dawn briefing at all.
12. **P3 · The step title on nights 1 and 2 is the full "Change the Summoner reminder token…" paragraph** — on nights 1 and 2 there is nothing to do but advance a counter; the step should read "Night 1 of 3 — nothing happens tonight" and the counter should advance itself.

## Proposed behaviour (spec)

### Setup

- Keep `Setup.modifierFor`'s `[No Demon]` handling (`Setup.kt:131-133`) — it works.
- `validateSetupState` (`GameActions.kt:503-561`) must special-case a Summoner game:
  - skip the "Marionette must neighbor the Demon" rule entirely when the bag contains no Demon; instead emit an informational note `Marionette enters play on night 3, next to the new Demon (Summoner jinx).`
  - add a positive check: `Summoner in play ⇒ exactly 0 Demons in the bag` (today this is only implied by the distribution maths).
- Setup briefing line: `No Demon until night 3. Nobody dies at night until the Summoner acts.`

### Night 1

- **when**: first night; wake condition = holder alive.
- **targets**: none.
- **immediate effects**: place `PlacedReminder("summoner","Night 1")` on the holder **automatically** when the first night begins.
- **information / visibility**:
  - The step gets the **same bluff card as DEMON_INFO** — lift the gate at `NightScreen.kt:783` from `step.id == DEMON_INFO` to `step.id in setOf(DEMON_INFO, "summoner")` (and generally: any step whose guide declares `kind: "bluffs"`).
  - Relabel the bluffs feature: when a Summoner is in play and no Demon is, the menu item and sheet title read **"Summoner bluffs"** (`GameShell.kt:219`, `BluffsSheet.kt:56`).
- **MINION_INFO adaptation** (`NightOrder.kt:60-80`): when no Demon exists, the detail must read `Wake all Minions (…). They see each other. There is no Demon yet — do not point one out.`
- **DEMON_INFO adaptation** (`NightOrder.kt:81-119`): when no Demon exists, **suppress the step entirely** and, if a Summoner is in play, note on the Summoner's step that the bluffs go here instead.

### Nights 2 and 3 (counter)

Generalise a **night-counter token** helper in `GameActions`:

```
advanceNightCounter(state, sourceId, labels = listOf("Night 1","Night 2","Night 3"))
```
called at the start of each night for every holder of a character declaring such a counter (Summoner, Xaan). It moves the token from `Night N` to `Night N+1` exclusively.

- Night 2 step text: `Night 2 of 3 — the Summoner does not act tonight. Nobody can be killed by a Demon yet.`
- Night 3 step: full resolver (below). **Pukka jinx**: if a Pukka is on the script, the resolver is also offered on night 2 with the label `Summon a Pukka early (jinx)`.

### Night 3 resolver (new `QuickResolutions` case, `NightScreen.kt:462-525`)

- **when**: other night, `state.cycle == 3` (or 2 with the Pukka jinx); wake condition = Summoner alive.
- **impairment branch** — if `StatusEffects.isImpaired(state, lookup, holder)`, show:
  `! The Summoner is drunk/poisoned. Per the Poisoner/Courtier jinx the Summoner still names the Demon, but YOU choose which player becomes it.` and let the Storyteller pick the player. *(See the unresolved rule question above — surface both readings rather than silently picking one.)*
- **targets**: two picks in one panel:
  1. **Player** — any player, alive or dead, including the Summoner themself ("The Summoner may choose any player to become the Demon, even themselves"). Sort: alive first. A **dead** target must show `! Zombuul jinx: a dead player summoned into the Zombuul has already "died once".`
  2. **Demon** — a character picker restricted to `team == DEMON` **on the current script**.
- **immediate effects**, applied atomically in one undoable action:
  - `assignCharacter(target, demonId)`, `setShownCharacter(target, null)`, `alignmentFlipped = false` (the new Demon is evil by its own team), i.e. reuse the `starPass` shape (`GameActions.kt:79-96`) minus the death.
  - Place `PlacedReminder("summoner","Night 3")` → replace with a spent marker `PlacedReminder("summoner","No ability")` on the Summoner.
  - Jinx follow-ups the resolver must offer as checkboxes/prompts:
    - **Legion**: `All evil players become Legion` → one tap to convert every evil seat.
    - **Lord of Typhon**: `Must neighbor a Minion; their other neighbor becomes an evil Minion` → open the seat-reorder dialog and a Minion picker.
    - **Marionette**: `The Marionette enters play now, neighbouring the new Demon` → pick the seat, set `characterId = marionette` + shown good token.
    - **Hatter / Kazali / Pit-Hag**: if a second living Demon now exists → banner `Deaths tonight are arbitrary (jinx).`
- **show cards**, in order, exactly as the wiki lists them:
  1. `YOU ARE` + the chosen Demon token (a `ShowCard.CharacterCard`, token = the picked Demon — the existing `token: "pick"` machinery at `NightScreen.kt:366-395` already supports this).
  2. `YOU ARE` + evil (the existing `ShowCard.AlignmentCard(evil = true)`, i.e. `kind: "evil"` in the guide).
- **visibility**: explicit line on the step — `Do NOT show the new Demon who the Minions are, and do not show the Minions their new Demon.`
- **deferred effects**: after the resolver runs, the night sheet must **re-scroll to the new Demon's step** and flag it: `<name> is now the <Demon> — they act tonight.` Today the step appears but nothing points at it.

### Win condition (`WinCheck.kt`)

Add, before the existing "every Demon is dead" branch:

```
if (summonerInPlay && demons.isEmpty()) {
    val summoner = players.find { it.characterId == "summoner" }
    if (summoner == null || !summoner.alive)
        return Advisory(goodWins = true,
            reason = "No Demon exists and the Summoner can no longer create one — good wins.",
            cautions = listOf(
              "Courtier/Preacher/Engineer jinx: if the Summoner merely lost their ability, the Storyteller has the Summoner ability instead.",
              "Check for an Alchemist-Summoner or a Pit-Hag who could still make a Demon."))
}
```
and, on night 3 specifically, if the Summoner is dead at the point their step would run, surface the same advisory.

### Day-start / dawn briefing entries

- Days 1–2: `No Demon is in play yet — nobody can die to a Demon tonight.`
- Dawn after night 3: `<name> is now the <Demon>.` plus, for a Leviathan, `Announce publicly: the Leviathan is in play; it is day 3 of 5.`

### Data changes

- `night_and_jinxes.json`: add the 10 missing Summoner jinxes; replace the Marionette jinx text with the current wiki wording; reconcile the Poisoner jinx against the wiki's Courtier/Preacher wording (see the open question).
- `night_guide.json:1443`: rewrite `other` — split nights 2 and 3, drop the "no Demon is created" default, add the "does not learn Minions" line, add the `YOU ARE` + evil card as a second `shows` entry (`kind: "evil"`).
- `characters.json:1877`: no change needed.

## Tests to add

1. **Summoner bag has no Demon** — already covered (`GameActionsTest.kt:231`, `SetupTest.kt:97`). Keep.
2. **Marionette + Summoner setup is legal** — *Given* seats holding a Summoner and a Marionette and no Demon, *When* `validateSetupState` runs, *Then* it returns **no** "must neighbor the Demon" issue. (Fails today: `GameActions.kt:540-543`.)
3. **No DEMON_INFO step without a Demon** — *Given* an 8-seat Summoner game on night 1, *When* `NightOrder.firstNight` runs, *Then* the returned steps contain no `DEMON_INFO` entry, and the `MINION_INFO` detail does not contain "point out the Demon".
4. **Good wins when the Summoner dies before night 3** — *Given* a Summoner game with no Demon and a dead Summoner, *When* `WinCheck.check` runs, *Then* it returns `goodWins = true`.
5. **No advisory while the Summoner still lives** — same game, Summoner alive, day 1 → `WinCheck.check` returns null.
6. **Night counter advances** — *Given* the Summoner holds `Night 1`, *When* `advancePhase` reaches night 2, *Then* they hold `Night 2` and not `Night 1`; at night 3, `Night 3` only.
7. **Summon converts the target** — *Given* a Snitch seat and the Summoner, *When* the summon action runs with (Snitch, `lleech`), *Then* the Snitch's `characterId == "lleech"`, `shownCharacterId == null`, `isEvil(lookup) == true`, and the Summoner holds a spent marker.
8. **The new Demon acts the same night** — after the conversion above, *When* `NightOrder.otherNight` is rebuilt for cycle 3, *Then* a `lleech` step is present and its index is greater than the `summoner` step's index.
9. **Summoning oneself** — *When* the summon target is the Summoner, *Then* the Summoner's `characterId` becomes the Demon and the game has exactly one Demon.
10. **Impaired Summoner still summons** — *Given* a `Poisoned` token on the Summoner on night 3, *Then* the resolver is offered (with the storyteller-picks-the-player note), not suppressed.
