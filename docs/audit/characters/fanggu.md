# Fang Gu (fanggu) — Sects & Violets Demon

## Official rules (sources)

Sources:
- https://wiki.bloodontheclocktower.com/Fang_Gu (Character Text, Summary, How to Run, Examples, Tips & Tricks, Jinx)
- https://wiki.bloodontheclocktower.com/Night_Order (other-night position among the Demons)

Current ability text (wiki, verbatim):

> "Each night*, choose a player: they die. The 1st Outsider this kills becomes an evil Fang Gu & you die instead. [+1 Outsider]"

`characters.json:1070` carries exactly this string — **no drift**.

How to Run (wiki):

- **Setup**: before the tokens go in the bag, add one extra Outsider token and remove one Townsfolk token (`[+1 Outsider]`).
- **Each night except the first**: wake the Fang Gu. They point at any player. Put them back to sleep. Then resolve in this order:
  1. Target is **not** an Outsider → the target dies (DEAD reminder).
  2. Target **is** an Outsider **and the ONCE reminder is already in the Grimoire centre** → the target dies normally (DEAD reminder). No further jumps ever happen.
  3. Target **is** an Outsider **and ONCE is not yet placed** → **the Fang Gu dies instead** (DEAD reminder on the *Fang Gu*, not on the target). The target does **not** die. Wake the target and show, in order: **YOU ARE** card → **Fang Gu** token → **YOU ARE** card → **thumbs-down** (evil) hand sign. Swap their character token for the spare Fang Gu token. They are now an **evil Fang Gu**. Place the **ONCE** reminder in the centre of the Grimoire, permanently.
- Reminder tokens: **DEAD**, **ONCE**. ONCE lives in the Grimoire *centre* (it is a game-global "the jump has happened" flag, not a per-player token) and never leaves.

Edge cases / clarifications that matter for the storyteller:

- **"The 1st Outsider this kills"** — the jump is keyed on the Fang Gu *killing* an Outsider. If the attack fails (Monk `Safe`, Innkeeper `Protected`, Soldier is not an Outsider so n/a, Tea Lady, Fool's first death, Sailor, Lleech host rule, Vigormortis-irrelevant, drunk/poisoned Fang Gu) then **nothing at all happens**: no death, no jump, and the once-per-game is **not** spent.
- **A drunk or poisoned Fang Gu** kills nobody and cannot jump; the ONCE is not spent.
- The chosen Outsider **does not die**, so on-death Outsider triggers do **not** fire. Wiki Example, verbatim: *"The Fang Gu attacks the Artist, who dies. The next night, the Fang Gu attacks the Sweetheart, who becomes the Fang Gu while the old Fang Gu dies. The Sweetheart does not make a player drunk, because they did not die. The next night, the new Fang Gu attacks the Klutz, who dies."*
- After the jump **there are two Fang Gu character tokens in the Grimoire**: the dead original and the living new one. This is why the historical night-sheet phrasing (still in `characters.json:1072`) says *"if that player was an Outsider and there are no other Fang Gu in play"*. For everything that asks "who is the Demon" (Clockmaker distance, Sage, Knight, Flowergirl "did the Demon vote", win conditions, Fortune Teller) **only the living Fang Gu counts**; the dead one is a dead Demon.
- The **new Fang Gu is not given Minion info** and learns nothing beyond "you are the Fang Gu, you are evil". Wiki Tips: *"If converted to new Fang Gu: locate the recently-deceased Demon first, then contact Minions."* The old Fang Gu is dead and can talk to them from the graveyard.
- The new Fang Gu **keeps their seat and their reminder tokens' physical position**, but their old character (and its ability) is gone.
- **Jinx — Scarlet Woman**: *"If there would be two Demons, one of which was the Scarlet Woman, the Scarlet Woman remains the Scarlet Woman."* Practically: when the Fang Gu dies from the jump, the Scarlet Woman **does not** become the Demon (the Outsider did). `night_and_jinxes.json:169-172` states this correctly in plainer words.
- The Fang Gu may target itself (it is "choose a player"); it is not an Outsider, so it simply dies.
- The Fang Gu may target a Traveller. Travellers are not Outsiders — no jump.

## What the app does today

Data (all correct, no drift):
- `engine/src/main/resources/botc/data/characters.json:1066-1078` — id/name/edition/team/ability/`setup: true`, `otherNightReminder` with the full jump script, reminders `["Dead","Once"]`.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:415` — `fanggu` sits in the other-night order between `po` and `nodashii`. **Correct** per the official order. No first-night entry. **Correct.**
- `engine/src/main/resources/botc/data/night_and_jinxes.json:169-172` — Scarlet Woman jinx present.
- `engine/src/main/resources/botc/data/night_guide.json:720-738` — a good other-night run-book plus two prepared show cards ("Fang Gu" self-token card, "You are evil" alignment card). **Works.**
- `Setup.kt:121` `modifierFor` parses `[+1 Outsider]`; covered by `SetupTest.kt:47`. **Works.**

Runtime:
- `NightOrder.kt:46-52` builds the step by grouping **all** players (dead included) by `nightRoleId`; `playerIds` is therefore every seat holding `fanggu`, in seat order.
- `app/.../NightScreen.kt:483-498` — `QuickResolutions` branch `"fanggu"`:
  - gated on `holder.alive`, where `holder = step.playerIds.firstOrNull()` (`NightScreen.kt:467`);
  - renders the generic `DemonKillPanel` (`NightScreen.kt:534-620`) **and, unconditionally below it**, a second "Fang Gu jump (once per game)" `ResolutionPicker`;
  - the jump picker's candidate list is *every alive player except the holder*, merely **sorted** Outsiders-first — Townsfolk, Minions and Travellers are all selectable;
  - confirming calls `GameActions.starPass(state, holder.id, target.id, lookup)` then `placeExclusiveReminder(target.id, PlacedReminder("fanggu","Once"))`.
- `GameActions.kt:79-96` `starPass` kills the Fang Gu with `DeathCause.OTHER_NIGHT_DEATH` and copies `characterId = "fanggu"` onto the heir with `shownCharacterId = null, alignmentFlipped = false` → the heir is evil (Demon default). The **old seat keeps `characterId = "fanggu"`** while dead.

Storyteller's experience, night 2: the Fang Gu row shows the long official reminder text plus the night-guide prose, then **two** stacked pickers with no branching between them — a "Demon kill — who did X choose?" chip row, and below it a jump chip row. The ST has to know which one to use. Nothing checks Outsider-ness, nothing checks whether the jump already happened, nothing checks protection on the jump path.

## Defects and gaps

1. **P0 · After the jump the Fang Gu step loses its kill tool entirely (seat-order dependent).**
   `starPass` leaves the dead original with `characterId = "fanggu"` (`GameActions.kt:88-94`) and `NightOrder.kt:46-49` groups without an alive filter, so the step's `playerIds` contains both seats in seat order. `QuickResolutions` takes `playerIds.firstOrNull()` (`NightScreen.kt:467`) and gates on `holder.alive` (`NightScreen.kt:483`). If the **dead** original sits at a lower index than the new Fang Gu, `holder.alive == false` and **neither the `DemonKillPanel` nor the jump picker renders** — the storyteller gets prose only and must kill by hand from the seat sheet. If the new Fang Gu happens to sit lower, everything works. Same latent bug for the Imp star-pass.
   *Repro*: seats 1..8, Fang Gu at seat 2, Outsider at seat 6. Night 2, jump into seat 6. Night 3 → open the Fang Gu step → no kill panel; the header shows "Ada †, Farah".
   *Fix scope*: `Player.hasAbility`-aware holder selection everywhere a step picks a holder (`NightScreen.kt:467`, `NightScreen.kt:837` (`InfoCalc` holder)).

2. **P0 · The once-per-game jump is never enforced or even checked.**
   The picker at `NightScreen.kt:486-498` is rendered every night regardless of whether a `("fanggu","Once")` token exists. The ST can perform a second, third… jump. Also, `placeExclusiveReminder` (`GameActions.kt:194-201`) *moves* the single ONCE token onto the new seat, so even the record of the first jump migrates rather than staying fixed.
   *Repro*: jump on night 2, then jump again on night 3 — the app happily does it.

3. **P0 · Any player can be jumped into, not only Outsiders.**
   `NightScreen.kt:490-492` filters `it.alive && it.id != holder.id` and only *sorts* Outsiders first. Selecting a Townsfolk turns them into an evil Fang Gu with no warning. Travellers are offered too.

4. **P1 · The jump path performs no protection / impairment checks.**
   The `DemonKillPanel` warns when the Demon is impaired (`NightScreen.kt:547-553`) and prints `StatusEffects.deathNotes` for the target (`NightScreen.kt:588-590`). The jump `ResolutionPicker` (`NightScreen.kt:486`) prints neither. A Monk-protected Outsider, a Fool, a Tea-Lady-guarded seat, or a poisoned Fang Gu will all silently produce a jump that the rules forbid (a failed attack kills nobody and spends nothing).

5. **P1 · Two competing panels with no branching — the ST must know the rule to pick the right one.**
   `NightScreen.kt:484-498` renders `DemonKillPanel` *and* the jump picker simultaneously. Killing an Outsider through the ordinary panel silently skips the mandatory jump; using the jump picker on a Townsfolk silently breaks the rule. The step should be a **single** "who did the Fang Gu choose?" picker that resolves the rule itself.

6. **P1 · The old Fang Gu keeps `characterId = "fanggu"`, poisoning every "who is the Demon" query.**
   `InfoCalc.clockmaker` (`InfoCalc.kt:220`) takes `indexOfFirst { team == DEMON }` → may measure from the **dead** Fang Gu. `InfoCalc.knight` (`:434`), `sage` (`:424`) and `flowergirl` (`:314`) all collect *all* Demon-team seats, so the dead original's ghost vote can make the Flowergirl hear "YES — the Demon voted today", and the Knight can be told to point away from a corpse.
   *Repro*: jump on night 2; night 3 open the Clockmaker step — distance is measured to whichever Fang Gu seat comes first.

7. **P1 · The ONCE token is a per-seat reminder, not a game flag.**
   Official placement is the Grimoire **centre**. `NightScreen.kt:496` puts it on the new Fang Gu. There is no centre/global reminder concept (`GameState` has none), so the flag is fragile: a later Pit-Hag change or a manual token clean-up on that seat erases the game's only record that the jump is spent.

8. **P1 · The Scarlet Woman jinx is never surfaced at the moment it matters.**
   The jinx text exists (`night_and_jinxes.json:169-172`) and is browsable via the "Jinxes in play" menu, but when the Fang Gu dies from the jump the app says nothing. Conversely `StatusEffects.deathNotes` **would** print *"Scarlet Woman becomes the Demon (5+ alive)"* (`StatusEffects.kt:104-107`) if the ST kills the Fang Gu through the seat sheet or the ordinary demon panel — which is exactly the wrong advice on a jump night.

9. **P2 · Nothing tells the ST what the new Fang Gu must be shown, in order, at the moment of the jump.**
   The two prepared cards ("Fang Gu" token, "You are evil") sit in the step's show-card chips (`night_guide.json:724-736`) but are not sequenced with the jump action, and the mandatory **YOU ARE** prefix / thumbs-down ordering is only described in prose.

10. **P2 · No dawn/day-start briefing after a jump.**
    The morning after the jump the ST must remember: announce the old Fang Gu's death; the Outsider's old character is *gone* from play (relevant to the Undertaker, Dreamer, Artist, Savant and to any "not in play" bluff logic); the new Fang Gu has no Minion info. Nothing surfaces any of this.

11. **P2 · `DeathCause.OTHER_NIGHT_DEATH` for the old Fang Gu is untyped.**
    `GameActions.kt:87`. It is neither an execution nor a Demon kill, which is right, but the game log (`GameExtras.kt:54`) then reads as a generic night death with no explanation of *why* the Demon died. A `DeathCause` or death-record note of "Fang Gu jump" would make the log readable.

12. **P3 · The new Fang Gu inherits the Outsider's stale reminder tokens.**
    `starPass` does not clear tokens, so a leftover `Red herring`, `Mad`, `Grandchild`, `Drunk` (from a Sweetheart) etc. stays on the seat and keeps affecting `isImpaired` / `InfoCalc`.

13. **P3 · The jump picker offers Travellers.** Travellers cannot be jumped into; they should not appear (`NightScreen.kt:490`).

## Proposed behaviour (spec)

### Night action

- **when**: other nights only (never first night). Wake condition: the seat holding `fanggu` that is **alive**. If several seats hold `fanggu` (post-jump), the acting one is the alive one — resolve the holder as `step.playerIds.firstOrNull { it.alive } ?: step.playerIds.firstOrNull()`.
- **targets**: exactly 1. Constraint: any player including self and Travellers (the *kill* is unrestricted). Picker defaults to none; sort alive-first, then seat order; label dead seats with `†` and disable them (the Fang Gu cannot kill the dead).
- **immediate effects** — a *single* confirm button whose label and behaviour are computed from the target:

  ```
  jumpAvailable = state has no ("fanggu","Once") marker anywhere
                  AND target.team == OUTSIDER
                  AND !isImpaired(fangGu)
                  AND deathNotes(target) contains no attack-blocking protection
  ```

  | case | button label | effect |
  |---|---|---|
  | Fang Gu impaired | "Attack fails — no one dies" | nothing; step marked done |
  | protection on target (`Safe`/`Protected`/Soldier/Fool-first/Tea Lady/Sailor/…) | "Protected — confirm no death" (with the reason listed) | nothing |
  | target not an Outsider | "`<name>` dies" | `kill(target, DeathCause.DEMON)` |
  | target is an Outsider, ONCE already placed | "`<name>` dies (jump already used)" | `kill(target, DeathCause.DEMON)` |
  | `jumpAvailable` | "JUMP — `<fangGu>` dies, `<name>` becomes an evil Fang Gu" | see below |

  Jump effect, atomically:
  1. `kill(fangGuId, DeathCause.FANG_GU_JUMP)` (new cause, or `OTHER_NIGHT_DEATH` with a `note = "Fang Gu jump"` on the `DeathRecord`).
  2. Target: `characterId = "fanggu"`, `shownCharacterId = null`, `alignmentFlipped = false`, **reminders cleared** except tokens the rules keep (recommend: clear all — the Grimoire token was physically replaced).
  3. Set a **game-level** flag `fangGuJumped = true` on `GameState` (see data changes) — *not* a per-seat reminder.
  4. Do **not** kill the target and do **not** fire any on-death trigger for the target.
  5. Suppress the Scarlet-Woman advisory for this death (see interactions).
  6. Immediately present the ordered show sequence (below).

- **deferred effects**:
  - At **dawn**: the death announcement must name only the old Fang Gu.
  - **Day start briefing** the morning after a jump: "`<old>` died as the Fang Gu. `<new>` is now an evil Fang Gu — their old `<Outsider>` ability is gone and that character is no longer in play. The new Fang Gu has not been told who the Minions are."
  - From the next night, the `fanggu` step must resolve to the **living** holder.
- **expiry**: the jump flag never expires. No nightly tokens.
- **information**: none given by the Fang Gu. What is *shown* to the new Fang Gu, in this exact order, as four full-screen cards with a "Next" button:
  1. **YOU ARE**
  2. the **Fang Gu** character token
  3. **YOU ARE**
  4. **evil** (thumbs-down / evil alignment card)
- **visibility**:
  - New Fang Gu: the four cards above and nothing else — explicitly **no** Minion info, **no** bluffs.
  - Minions: told nothing by the app; the ST should be reminded that Minions who need the Demon's identity (Witch, Cerenovus, Pit-Hag targeting) may now be acting on stale information.
  - Lunatic: unaffected — but if a Lunatic is in play, the Demon-info annotation at `NightOrder.kt:157-171` must follow the **living** Fang Gu.
- **day-time inputs**: none.
- **interactions/jinxes to handle explicitly**:
  - **Scarlet Woman**: when the Fang Gu dies *from the jump*, do not offer the "Scarlet Woman becomes the Demon" advisory (`StatusEffects.kt:104-107`) and do not raise a `WinCheck` prompt (a living Demon exists). Surface the jinx text as a one-line note on the resolution: "Jinx: the Scarlet Woman does **not** catch this death."
  - **Monk / Innkeeper / Soldier / Fool / Sailor / Tea Lady / Lleech**: block the attack → block the jump → **do not** spend the once-per-game.
  - **Drunk** (an Outsider!): a legal jump target. The resulting Fang Gu is a real Demon; clear `shownCharacterId`.
  - **Recluse**: an Outsider who *may register as* a Minion/Demon. The Fang Gu jump keys on the *true* type, so a Recluse **is** a valid jump target. Offer an ST toggle rather than deciding silently.
  - **Spy**: a Minion who may register as an Outsider. Official misregistration is storyteller choice — offer "treat as Outsider for the jump?" when the Spy is targeted; if yes, the jump happens.
  - **Vortox / No Dashii / Vigormortis**: cannot co-exist (one Demon), except via Legion/Kazali variants — out of scope.
  - **Pit-Hag** turning someone into a Fang Gu: the jinx-free case; the app must then handle two living Fang Gu. The ONCE flag is game-global and already spent or not.

### UI text the step should display

- Header: **"Fang Gu — who did `<name>` choose?"**
- Under an Outsider target with the jump available: **"OUTSIDER — this is the jump. `<fangGu>` dies; `<target>` becomes an evil Fang Gu and does NOT die. Once per game."**
- Under an Outsider target with the jump spent: **"Jump already used tonight-or-earlier — `<target>` just dies."**
- Impaired: **"`<name>` is drunk/poisoned — the attack fails. No death, no jump, the jump is NOT spent."**
- After resolving a jump: **"Show `<target>`: YOU ARE → Fang Gu → YOU ARE → evil."** with the 4-card sequence button.

### Data changes

- `GameState`: add `val fangGuJumped: Boolean = false` (or a general `gameFlags: Set<String>` — see the cross-cutting note; a Grimoire-centre token concept would serve Fang Gu ONCE, Pit-Hag, Boomdandy and others).
- `characters.json:1072`: keep the official night-sheet wording, but the app should stop relying on the ST reading "there are no other Fang Gu in play".
- `night_guide.json:722`: add the explicit failure cases — "If the Fang Gu is drunk or poisoned, **or the chosen Outsider is protected**, nothing happens and the jump is NOT used up."
- `night_guide.json` `shows`: convert the two cards into an ordered 4-card sequence (`YOU ARE`, token, `YOU ARE`, evil).
- No night-order changes needed — `fanggu` at `night_and_jinxes.json:415` is correct.

## Tests to add

1. **Post-jump holder resolution.**
   *Given* seats `[A=fanggu(seat0), B=chef, C=sweetheart(seat2), …]`, *when* the Fang Gu jumps into C, *then* `NightOrder.otherNight(...)`'s `fanggu` step must expose a holder that is **alive** (C), and the UI holder resolver must return C, not A. Fails today (returns A).

2. **Once-per-game is enforced.**
   *Given* a state where the jump has already happened, *when* the Fang Gu chooses another Outsider, *then* the resolver returns "target dies" (a normal `kill`) and the Fang Gu stays alive. Fails today (a second jump is offered and works).

3. **Jump requires an Outsider.**
   *Given* the Fang Gu chooses a Townsfolk, *then* no jump resolution is offered/possible. Fails today.

4. **Protected Outsider blocks the jump and does not spend it.**
   *Given* an Outsider with `PlacedReminder("monk","Safe")`, *when* the Fang Gu targets them, *then* no one dies, the Outsider is unchanged, and the jump flag is still unset. Fails today.

5. **Impaired Fang Gu cannot jump.**
   *Given* the Fang Gu has `PlacedReminder("poisoner","Poisoned")`, *when* they target an Outsider, *then* nothing happens and the flag is unset. Fails today.

6. **The jumped Outsider does not die and their on-death trigger does not fire.**
   *Given* the target is the Sweetheart, *then* `state.deaths` gains exactly one record (the old Fang Gu), the Sweetheart's seat is alive, and no `Drunk` token is placed. Partly passes today (no death recorded for the target) but there is no assertion and no ST guidance.

7. **Only the living Fang Gu counts as "the Demon".**
   *Given* a post-jump state, *then* `InfoCalc.compute(..., "clockmaker", ...)` measures from the living Fang Gu, `InfoCalc` `knight`/`sage` list only the living one, and `flowergirl` ignores a ghost vote cast by the dead one. Fails today on all four.

8. **Scarlet Woman does not catch a jump death.**
   *Given* a Scarlet Woman and 6 alive, *when* the Fang Gu jumps, *then* `StatusEffects.deathNotes` for the old Fang Gu contains **no** "Scarlet Woman becomes the Demon" note, and `WinCheck.check` returns no advisory (a Demon lives). Fails today if the ST routes the death through `deathNotes`.

9. **Setup modifier.** *Given* a 9-player Fang Gu game, *then* the required distribution is 5/2/1/1 (`SetupTest.kt:47` already covers the modifier; extend to the full distribution). Passes today.

10. **Reminder hygiene.** *Given* the jump target carried `PlacedReminder("fortuneteller","Red herring")`, *then* after the jump their reminders are cleared. Fails today.
