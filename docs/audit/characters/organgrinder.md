# Organ Grinder (organgrinder) — exp (Carousel) minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Organ_Grinder>

Current ability text:

> "All players keep their eyes closed when voting and the vote tally is secret.
> Each night, choose if you are drunk until dusk."

`characters.json:1850` matches this text exactly — **no drift**.

### How to run (wiki, verbatim sentences, in order)

- "Each night, wake the Organ Grinder. The Organ Grinder either nods or shakes their head."
- "If they nod their head, mark them with the DRUNK reminder."
- "Put the Organ Grinder to sleep."
- "When a player has been nominated and a vote is just about to begin, and the Organ Grinder is sober, ask all players to close their eyes."
- "If they ask why, tell them that an Organ Grinder is in play."
- "When counting votes, do so silently."
- "Afterwards, do not reveal how many players voted, nor if the nominee is 'about to die'."
- "If there were enough votes to execute the nominee, mark them with the ABOUT TO DIE reminder."
- "Ask players to open their eyes, and if there are any more nominations."
- "When nominations are closed, declare that the player marked ABOUT TO DIE, is executed."
- "Each dusk, remove the DRUNK reminder."
- "Players are not allowed to use other methods to determine who is voting, such as touch or sound."

### Key clarifications

- **Drunk switches the eyes-closed rule OFF**: "If the Organ Grinder is drunk, the vote happens with eyes open, as normal." So a nodded-yes Organ Grinder gives the town a normal, public day. The nightly choice is the Organ Grinder's own drunkenness; the eyes-closed/secret-tally clause is a continuous ability that applies whenever the Organ Grinder is alive and sober.
- **Ghost votes**: "Dead players may vote once if they have a vote token. Their vote token is removed at the end of the day instead of after the vote." (The delay is a secrecy measure — the token disappearing would otherwise leak who voted. A dead player still gets only one vote per game.)
- **Announcements**: the Storyteller announces neither the tally nor "about to die" — only, once nominations close, that the marked player is executed.
- Reminder tokens: `ABOUT TO DIE`, `DRUNK` (both present in `characters.json:1850`).

### Example (verbatim)

> "There are 8 players alive. The Noble is nominated. All players close eyes to vote and the Noble gets 5 votes. The Imp is nominated. All players close eyes to vote and the Imp gets 7 votes. The Pixie is nominated. All players close eyes to vote and the Pixie gets 4 votes. After nominations close, the Storyteller declares that Doug (the Imp) is executed and dies, and that good has won."

### Jinxes (wiki)

- **Butler**: "If the Organ Grinder is causing eyes closed voting, the Butler may raise their hand to vote but their vote is only counted if their master voted too."
- **Alchemist**: "If the Alchemist has the Organ Grinder ability, the Organ Grinder is in play. If both are sober, both are drunk."

## What the app does today

Data paths:
- `engine/src/main/resources/botc/data/characters.json:1850` — text, reminders `["About To Die","Drunk"]`. Correct.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:328` (firstNight index 33) and `:394` (otherNight index 21) — wakes both nights, in the right slot (after Snake Charmer/Godfather, before Devil's Advocate). Correct.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:10` — the Butler jinx is present (text matches the wiki).
- `engine/src/main/resources/botc/data/night_guide.json:1419` — first/other prose telling the ST to nod/shake, place or remove Drunk by hand, and to remember eyes-closed voting all day. One show card, "Ask" → `DRUNK TODAY? NOD YES / SHAKE NO`.

Storyteller experience:
1. Night: the step reads *"The Organ Grinder either nods their head yes to be drunk, or shakes their head no to be sober."* plus the guide prose. `QuickResolutions` (`app/.../screens/NightScreen.kt:462-525`) has **no** `"organgrinder"` case, so the only tool is the generic reminder tray (`NightScreen.kt:193-357`): tap "Drunk", then tap the Organ Grinder's seat. Removing it on a "no" night is a separate trip to `SeatSheet` (`SeatSheet.kt:324+`, tap-to-remove).
2. Dawn/dusk: `GameActions.EXPIRES_AT_DUSK` (`GameActions.kt:231-242`) does **not** contain `"organgrinder" to "Drunk"`, so the token never expires.
3. Day: `DayScreen.kt` runs a completely public flow — a live "N so far, needs X" counter (`DayScreen.kt:174-178`), a bold "<name> is about to die" line (`DayScreen.kt:206-216`), an "On the block: <name>" banner (`DayScreen.kt:104-114`), a per-nomination "<n> votes · about to die" record (`DayScreen.kt:339-348`), and the header "$aliveCount alive · $threshold votes to execute · $highest votes is the tally to beat" (`DayScreen.kt:87-92`). Nothing anywhere changes when an Organ Grinder is alive.
4. Ghost votes are spent the moment a nomination is recorded (`DayScreen.kt:232-240`), and a spent dead voter's chip is disabled for the rest of the day (`DayScreen.kt:184`).
5. The Butler jinx text is visible only in `SeatSheet.kt:222-234` and the "Jinxes in play" dialog (`GameShell.kt:243`, `GameExtras.kt:200-230`). Nothing fires at vote time.

## Defects and gaps

1. **P0 · The Drunk token never expires at dusk** — the wiki says "Each dusk, remove the DRUNK reminder"; the app's own guide text at `night_guide.json:1419` repeats it, but `GameActions.EXPIRES_AT_DUSK` (`GameActions.kt:231-242`) has no `organgrinder`/`Drunk` pair, so `clearEphemeral` (`GameActions.kt:244-251`) leaves it. Consequence: from night 2 on the grimoire says the Organ Grinder is drunk forever, `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) reports them impaired, and a Storyteller reading the grimoire runs eyes-open voting on a day when the Organ Grinder actually shook their head. Repro: night 1 place "Drunk" on the Organ Grinder → Dawn → Dusk → the token is still there on night 2.
2. **P0 · The day screen publicly leaks the tally and the block** — with a sober, living Organ Grinder the Storyteller must "do so silently" and "not reveal how many players voted, nor if the nominee is 'about to die'". The app puts the running count, the pass/fail verdict, the on-block banner and the votes-to-beat number on the same screen the Storyteller reads from and often reads aloud, with no mode switch. `DayScreen.kt:87-92, 104-123, 174-178, 206-216, 339-348`. Repro: put an Organ Grinder in play, go to the Day tab, start a nomination — the UI is identical to a non-Organ-Grinder game.
3. **P1 · No "close your eyes" prompt at nomination time** — the rule fires "when a player has been nominated and a vote is just about to begin"; the app never says it. The Storyteller must remember it for every nomination of every day. `DayScreen.kt:161-178`.
4. **P1 · The nightly yes/no choice needs two taps in two different screens** — nod = tray "Drunk" → seat; shake = go to `SeatSheet` and tap the token off. There is no `"organgrinder"` case in `QuickResolutions` (`NightScreen.kt:462-525`) even though this is the single most repeated Organ Grinder action in the game.
5. **P1 · Butler jinx is never enforced at the vote** — `night_and_jinxes.json:10` carries the correct text, but `DayScreen.kt`'s voter chips (`:183-196`) do not know that, while the Organ Grinder is causing eyes-closed voting, a Butler hand only counts if the master's hand is up too. The Butler's `Master` token is already in state (`GameActions.kt:238`), so this is computable. Repro: Butler + Organ Grinder in play, tap the Butler as a voter without their master — the tally counts them.
6. **P1 · Ghost-vote tokens are spent immediately, not at end of day** — the wiki explicitly changes this under the Organ Grinder ("removed at the end of the day instead of after the vote"). In the app, `DayScreen.kt:232-240` flips `ghostVoteUsed` on Record and `:184` then disables that seat's chip, so the Storyteller cannot even mark that a dead player raised their hand again (which, with eyes closed, is exactly the mistake that happens at a real table and that the Storyteller must silently not count). The one-vote-per-game limit is still correct; what is missing is the deferred token removal and a way to record an uncounted hand. *(Rule note: I read the wiki sentence as a secrecy measure, not as granting a second vote — flagged P1 rather than P0 for that reason.)*
7. **P2 · The Alchemist–Organ Grinder jinx is missing from the dataset** — `night_and_jinxes.json` has only the Butler pair; the wiki also lists "If the Alchemist has the Organ Grinder ability, the Organ Grinder is in play. If both are sober, both are drunk."
8. **P2 · The Organ Grinder's own `About To Die` token is never used** — the app derives the block from `GameActions.aboutToDie` (`GameActions.kt:296-306`), which is fine and arguably better, but a Storyteller who is also running a physical grimoire gets no cue to place the physical token, and the derived block is displayed *publicly* (see defect 2) rather than as a private marker.
9. **P3 · Nothing tells the Storyteller the stock answer to "why are we closing our eyes?"** — "tell them that an Organ Grinder is in play" is a genuinely useful line to have on screen.

## Proposed behaviour (spec)

### Night step (both nights)

- **when**: both; wake condition = holder alive. (A dead Organ Grinder has no ability; the row should still render but greyed with "dead — skip", which `NightScreen.kt:751-757` already does.)
- **targets**: none.
- **immediate effects**: a two-button resolver, not a token tray:
  - `[ Nods — drunk until dusk ]` → `placeExclusiveReminder(organgrinder, "Drunk")` on the holder.
  - `[ Shakes — sober tonight ]` → remove any `organgrinder`/`Drunk` token from the holder.
  - The currently-selected answer must be visibly latched so re-opening the step shows what was chosen.
- **deferred effects**: none at night. At **day start**, the day briefing must state one of:
  - "SECRET VOTING today — Organ Grinder is sober. Eyes closed for every vote; never announce the tally or who is about to die." or
  - "Normal voting today — the Organ Grinder chose to be drunk."
- **expiry**: `organgrinder` / `Drunk` → **EXPIRES_AT_DUSK** (add to `GameActions.kt:231-242`).
- **information**: none computed.
- **visibility**: nothing shown to other players at night. The Storyteller may tell anyone who asks that an Organ Grinder is in play.

### Day: secret-vote mode

Define a derived predicate in the engine (new, e.g. `DayRules.secretVoting(state, lookup)`):

```
secretVoting = players.any { it.characterId == "organgrinder" && it.alive
                             && !StatusEffects.isImpaired(state, lookup, it) }
```

When `secretVoting` is true, `DayScreen` switches to a secret-vote layout:

- Before the tally opens, a full-width banner: **"EYES CLOSED — ask everyone to close their eyes, then count hands silently. Announce nothing."** with a one-line footnote "If asked why: an Organ Grinder is in play."
- The running voter count is **hidden behind a "Peek" press-and-hold** (or shown only in a small, dim, hard-to-read-aloud style); the "X is about to die / is safe / tie" verdict line is replaced by a neutral **"Tally recorded"** until the day's nominations are closed.
- The "On the block" banner (`DayScreen.kt:93-115`) becomes a private, collapsed **"Block (tap to reveal)"** control.
- Per-nomination history rows show `— votes hidden —` unless the row is tapped.
- A **"Close nominations"** action then reveals: "Execute <name>" (or "No execution today"), matching "When nominations are closed, declare that the player marked ABOUT TO DIE, is executed."
- Butler check: when the Butler's chip is tapped and their `Master` token holder is not also in `voters`, show inline `! Butler's vote does not count — their master has not voted (Organ Grinder jinx)` and exclude them from the tally (with an override tap).
- Ghost votes: keep the `ghostVoteUsed` flag for the tally rule, but defer the *visible* removal to dusk, and allow re-tapping a spent dead voter with the label "hand raised, not counted" so the Storyteller can record what actually happened at the table without changing the count.

### UI text the step should display

- Night step title line: `Organ Grinder — drunk tonight?`
- Buttons: `Nods (drunk until dusk)` / `Shakes (sober)`
- Night footer: `Sober ⇒ tomorrow's votes are eyes-closed and the tally is secret.`
- Day banner (secret mode): `EYES CLOSED VOTE — count hands silently. Do not say the number. Do not say who is about to die.`
- Day banner (drunk mode): `Organ Grinder is drunk today — vote normally, eyes open.`

### Data changes

- `night_and_jinxes.json`: add the Alchemist–Organ Grinder jinx.
- `night_guide.json:1419`: replace "add or remove the Drunk reminder accordingly" with a pointer to the two-button resolver, and add the "if they ask why, tell them an Organ Grinder is in play" line and the ghost-token rule.
- `GameActions.kt:231-242`: add `"organgrinder" to "Drunk"`.

## Tests to add

1. **Drunk expires at dusk** — *Given* an Organ Grinder marked with `PlacedReminder("organgrinder","Drunk")` during night 1, *When* `advancePhase` runs NIGHT→DAY→NIGHT, *Then* the token is gone before night 2 begins. (Fails today: the pair is not in `EXPIRES_AT_DUSK`.)
2. **Secret voting predicate — sober** — *Given* an alive, unimpaired Organ Grinder, *Then* `DayRules.secretVoting` is true.
3. **Secret voting predicate — self-drunk** — *Given* the same Organ Grinder holding their own `Drunk` token, *Then* `secretVoting` is false (wiki: "the vote happens with eyes open, as normal").
4. **Secret voting predicate — dead** — *Given* the Organ Grinder is dead, *Then* `secretVoting` is false.
5. **Secret voting predicate — poisoned by someone else** — *Given* a Poisoner `Poisoned` token on the Organ Grinder, *Then* `secretVoting` is false.
6. **Butler jinx tally** — *Given* Organ Grinder sober, Butler with `butler`/`Master` on Alice, *When* a tally includes the Butler but not Alice, *Then* the counted votes exclude the Butler; *When* it includes both, *Then* the Butler counts.
7. **Day briefing text** — *Given* a sober Organ Grinder at day start, *Then* the day briefing contains "EYES CLOSED"; *Given* a self-drunk one, *Then* it contains "vote normally".
