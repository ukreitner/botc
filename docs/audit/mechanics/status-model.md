# Status & death model (status-model) — impairment, protection, death, resurrection

Scope: `StatusEffects.kt` (`isImpaired`, `derivedPoison`, `deathNotes`, `nominationWarnings`),
`GameActions.kill/revive/resurrect`, `DeathRecord`, and every UI path that kills a player.
This is the *mechanics* layer that ~40 character specs all depend on: Sailor, Tea Lady, Monk,
Soldier, Pacifist, Minstrel, Vigormortis, No Dashii, Xaan, Widow, Zombuul, Fool, Poisoner,
Courtier, Innkeeper, Sweetheart, Goon, Philosopher, Snake Charmer, Puzzlemaster, Village Idiot,
Marionette, Lleech, Cannibal, Barista, Assassin, Godfather, Gossip, Lycanthrope, Mayor, Vizier,
Devil's Advocate, Scapegoat, Storm Catcher, Angel, Toymaker, Undertaker, Professor, Shabaloth,
Bone Collector.

The single sentence this document exists to fix:

> **The app has no model of "why", only of "what the label says".** Impairment is a substring
> search over English token text, protection is a list of warning strings that the storyteller
> must read and act on, and a death is an unconditional row in a list. Nothing knows who caused
> an effect, when it ends, whether its source still has an ability, or what should happen next.

---

## Official rules (sources)

### A. Drunk and poisoned are the same thing

<https://wiki.bloodontheclocktower.com/States>

> "At any given time, a player is either sober or drunk, and either poisoned or healthy.
> **Being drunk and being poisoned do the same thing.** Alive and dead players alike can be
> drunk or poisoned."

> "**Drunkenness and poisoning do not cancel out.** A poisoned drunk does not become sober or
> healthy! They're just both poisoned and drunk."

> "**A drunk or poisoned player has no ability.** A drunk Slayer cannot slay anybody, a poisoned
> Demon cannot kill anyone… If a player tries to use their 'once per game' ability while drunk or
> poisoned, they do not get to use it again. It is gone."

> "**You can give them false information.** … You're not required to give incorrect info, but you
> can—and you usually should!"

So: one status, two labels; the labels differ only in *source* and *duration*. False info is a
storyteller **choice**, not a mechanical consequence.

**Standing abilities are suspended, and resume.** <https://wiki.bloodontheclocktower.com/States>:

> "Normally, if an ability is a permanent ability or is already affecting the game, the player
> loses their ability when they become drunk or poisoned, and **that ability resumes when they
> become sober and healthy again**." — with the worked examples "The Tea Lady is poisoned, so she
> does not protect her neighbors. Later, the Tea Lady becomes healthy, so she protects her
> neighbors again" and the Witch's curse switching off and back on.

**One-shots are wasted, not suspended.** Same page: "If an ability is triggered or used when the
player is drunk or poisoned, the ability is wasted. It has no effect now, and no effect later on."

### B. Every effect ends when its source loses their ability — this is the load-bearing rule

<https://wiki.bloodontheclocktower.com/Abilities>:

> "**Abilities are lost immediately on death, poisoning, or drunkenness.** If a character dies,
> they lose their ability immediately and any of its persistent effects end, so you can remove
> their reminder tokens. **For example, if a Poisoner poisons the Slayer at night, then the
> Poisoner dies later that same night, the Slayer is no longer poisoned. Even though the
> Poisoner's ability says that it lasts 'until dusk', once the Poisoner dies, they lose their
> ability and its persistent effect ends.**"

> "if Julian poisons Amy, and then Evin poisons Julian, then Amy is no longer poisoned."

> "You can ignore these reminder tokens for the time being since drunk or poisoned players have
> no ability, **as if they were dead**. … if the sober Innkeeper protects the Chambermaid, but
> then the Innkeeper becomes drunk, the Chambermaid stops being protected."

Glossary, *Dead*: "When a player dies … they immediately lose their ability, and any persistent
effects of their ability immediately end."

Per-character confirmations of the same rule:
No Dashii — "If a No Dashii dies or otherwise loses their ability, then those two players become
healthy." Vigormortis — "If the Vigormortis dies or otherwise loses their ability, then those
players become healthy again." Philosopher — "If the Philosopher then dies or becomes drunk or
poisoned, the player they are making drunk becomes sober again." Widow — "The Empath is poisoned
due to the Widow. The Widow becomes drunk due to the Innkeeper. The Empath is no longer poisoned.
The Innkeeper dies. The Widow is now sober and the Empath is poisoned again." Xaan — "The Xaan
needs to be alive in order to poison."

**Consequences for the model.** `source alive && source sober` is not a special case for a handful
of positional characters — **it is the default for every effect in the game**, and it is
*recursive and reversible*. Two effects that outlive their source are explicit exceptions
(Puzzlemaster "even if you die"; Sweetheart "from now on" — the Sweetheart is already dead when it
fires; Snake Charmer's permanent poison; the Drunk/Marionette, whose "source" is their own
character).

### C. Protection: "safe from the Demon" vs "can't die"

| Protection | Blocks | Does not block | Source |
|---|---|---|---|
| **Soldier** (innate), **Monk** SAFE | every harmful effect of **the Demon's own ability** — kill, No Dashii/Vigormortis poison, Fang Gu jump, Lord of Typhon, and **the Imp's star pass** | execution (even a Demon-led one), Godfather, Gossip, Assassin, Witch, Lycanthrope, exile, storyteller deaths | Soldier: "protected from the Demon's ability to kill, **not the actions of the Demon player**"; "also protected from all other harmful effects of the Demon's ability". Monk: "The Monk protects the Imp. The Imp chooses to kill themself tonight, but nothing happens. The Imp stays alive and a new Imp is not created." |
| **Innkeeper** SAFE (×2) | **all night deaths** — "safe from being killed by the Demon… also safe from death caused by Outsiders, Minions, Townsfolk, and Travellers" | day deaths, executions — "The Innkeeper only protects players at night, not the day" | <https://wiki.bloodontheclocktower.com/Innkeeper> |
| **Sailor** (innate, self), **Tea Lady** CANNOT DIE (neighbours) | **everything**, day or night — "The Demon cannot kill them, nor the Godfather, nor the Gossip. If they are executed, they do not die." Exile too (Tea Lady example: "The Matron is exiled but remains alive.") | nothing except the Assassin | Tea Lady, Sailor ("If the sober Sailor is executed, declare that this player is executed but remains alive.") |
| **Assassin** | — | **overrides everything**: "This player dies, even if they are protected from death in any way, such as from an ability." "This cannot be prevented in any way (except if the Assassin doesn't have their ability…)" | <https://wiki.bloodontheclocktower.com/Assassin> |
| **Fool** | the **first** death of any kind; "If another character's ability protects the Fool from death, the Fool does not use their ability" — so it resolves **last** and is not consumed | a drunk/poisoned Fool dies, and the ability is spent | <https://wiki.bloodontheclocktower.com/Fool> |
| **Devil's Advocate** SURVIVES EXECUTION | execution only, the **following day** only; "declare that the player was executed but remains alive. (Do not say why.)" | night deaths | Devil's Advocate |
| **Pacifist** | an executed **good** player *might* not die — storyteller choice, every time | evil players; **exiles** ("a Bishop dies by exile — not an execution — so the Pacifist does not apply") | Pacifist |
| **Vizier** | "You cannot die during the day" — day deaths and execution; "The town nominates and executes the Vizier. The Vizier does not die. That night, The Demon kills the Vizier." | night deaths | Vizier |
| **Zombuul** | the **first** death only, converted into "lives but registers as dead" | second and later deaths; a **drunk or poisoned** Zombuul just dies ("If a drunk or poisoned Zombuul dies, good wins.") | Zombuul |
| **Lleech** | every death while the poisoned host is alive — "You die if & only if they are dead" | nothing once the host is dead | <https://wiki.bloodontheclocktower.com/Lleech> |
| **Storm Catcher** SAFE (Fabled) | every death except execution — "If they would die by other means, they remain alive"; "If the character marked with the Storm Catcher's SAFE reminder is executed, they die." | execution | <https://wiki.bloodontheclocktower.com/Storm_Catcher> |
| **Mayor** (redirect, not a block) | "During the night, if the Mayor would die, **you choose** if the Mayor actually dies, or if the Mayor remains alive and another character dies instead." Protection beats the bounce (Monk-protected Mayor: "Mayor's ability doesn't trigger; nobody dies") | day deaths, executions | Mayor |
| **Scapegoat** (substitution) | "If a player of your alignment is executed, you **might** be executed instead" — the original nominee lives; "The Scapegoat being killed still counts as an execution, so no more nominations occur today." | — | <https://wiki.bloodontheclocktower.com/Scapegoat> |
| **Angel** (Fabled) | nothing. It is a **post-hoc penalty**: "whoever is the single player most responsible for killing a protected player suffers some consequence" (storyteller picks: dies / loses ability for a day / cannot vote). "Remove the Angel on the final day." | — | <https://wiki.bloodontheclocktower.com/Angel> |
| **Toymaker** (Fabled) | nothing. It is a **Demon constraint**: "The Demon may choose not to attack & must do this at least once per game"; if a kill would end the game and the Demon has not yet skipped, "the Storyteller does not wake the Demon—they are forced to attack nobody tonight." | — | <https://wiki.bloodontheclocktower.com/Toymaker> |
| **Deviant** (Traveller) | "If you were funny today, you cannot die by exile." | — | characters.json |
| **Barista** SOBER AND HEALTHY | not a protection — an **anti-impairment**: "A player marked SOBER AND HEALTHY is sober and healthy (**even if they're also marked DRUNK or POISONED**) and always gets true information." Until dusk. | — | <https://wiki.bloodontheclocktower.com/Barista> |

### D. What counts as "the Demon kills you"

- **Sage**: "The Sage only gets this information when killed by a **Demon attack**. Being executed
  does not count." (Pit-Hag arbitrary death → no Sage info.)
- **Grandmother**: "If the Grandchild dies by any other means—such as execution, or another type
  of death at night—the Grandmother does not also die."
- **Choirboy**: "If the Demon kills the King **using their ability**… The Demon nominating and
  executing the King doesn't count. **Minions that kill the King, such as the Assassin, don't
  count either.**"
- **Banshee**: "If the Demon kills you…"
- **Ravenkeeper and Farmer are NOT Demon-gated** — any night death. Farmer: "Farmers that die
  during the day, such as by execution, do not create more Farmers."
- Vigormortis, No Dashii, Fang Gu, Po, Shabaloth, Pukka (the deferred death), Lleech, Zombuul,
  Kazali, Lord of Typhon, Ojo, Al-Hadikhia are the Demon's own ability → Demon kills.
- **Not** Demon kills: Assassin, Godfather, Gossip, Witch, Golem, Psychopath, Boomdandy,
  Moonchild, Lycanthrope, Gambler, Tinker, Harlot, Slayer, execution, exile, storyteller.
- **Uncertain / wiki silent** (flagged, do not guess in code — make it a storyteller toggle on the
  kill panel): Lil' Monsta, Legion, Yaggababble, Al-Hadikhia's "all die", Riot's nominee deaths,
  and a Demon dying to its own star pass. Indirect evidence: Riot carries jinxes with Sage,
  Ravenkeeper and Farmer ("a chosen Sage uses their ability but does not die"), which implies
  those abilities would otherwise fire.
- Lycanthrope blocks **the Demon's kill only**, including deferred ones ("The Magician was
  poisoned by the Pukka last night but does not die tonight, because the Pukka cannot kill
  tonight").

### E. Death, execution, exile

<https://wiki.bloodontheclocktower.com/States>:

> "**Execution is different from death.** Sometimes, a player may be executed but remain alive
> rather than die. Players may be executed multiple times, and **even dead players may be executed
> again**, just to be sure. Regardless of whether the group executes an alive or dead player,
> **this counts as the one execution allowed for the day**."

> "**A dead player cannot die again.** If a dead player is attacked by the Demon, for example,
> they do not die again."

Who reads *execution* and who reads *death by execution*:

| Consumer | Reads | Quote |
|---|---|---|
| Undertaker | **death** | "the execution does not cause a death (in which case the Undertaker learns nothing)" |
| Cannibal | **death** | "A player must be executed **and die** for the Cannibal to gain their ability." |
| Minstrel | **death** of a Minion | "does not trigger if a Minion is executed but doesn't die" |
| Saint / Mastermind / Evil Twin / Goblin / Boomdandy | **death** | per character pages |
| Godfather | a **day death** of an Outsider | "Outsiders that die at night don't count"; a Devil's-Advocate-saved Outsider does not arm it |
| Zombuul | any **day death** | "Each day, if a player dies, mark them with DIED TODAY"; "If a dead player is executed, the player can't die again, so the Zombuul would still wake" |
| Mayor (3-alive win) | **execution occurred** | "if exactly three players are alive and **no player was executed today**" — a bloodless execution still blocks the win |
| Vortox ("if no-one is executed, evil wins") | **execution occurred** | ability text |

Exile (Glossary): "The group decision to kill a Traveller during the day… Any players may support
an exile, **even dead players without a vote token**. **Abilities cannot affect an exile decision
in any way.** … **an exile is not an execution**." The exile *decision* is untouchable, but the
resulting *death* is still subject to "can't die" (Tea Lady: "The Matron is exiled but remains
alive") and to the Deviant.

### F. Resurrection

Glossary, *Resurrected / Regurgitated / Reborn / Raised*:

> "A dead player becoming alive again. When this happens, **the player gains their ability back,
> even if it was a 'once per game' ability that had been used. If this ability functions on the
> first night only, it functions tonight.**"

Shabaloth confirms: "The regurgitated player regains their ability, even a 'once per game' ability
already used. If they had a 'first night only' or 'start knowing' ability, they may use it again."

**This is the rule behind the user's complaint** ("When Professor brings someone back it should
remind in the morning and rerun the 1st night for that"): resurrection must (a) clear every
`No ability`/spent mark on that seat, and (b) re-queue their **first-night** step for tonight.

Not answered by the wiki (flag, do not guess): whether a resurrected player recovers a *spent
ghost vote*, and whether their earlier death still counts for the Undertaker / Zombuul "died
today". Recommendation: the death record stays (Undertaker already learned it that night; the
Zombuul's DIED TODAY was a fact about that day), the ghost vote resets to unspent because the
player is alive again, and the storyteller can override both.

### G. Impairment source table — the durations the model must express

| Source | Target | Ends |
|---|---|---|
| **Drunk** (character) | self | never (only a character change) |
| **Poisoner** | chosen | dusk of the following day ("tonight and tomorrow day") **or the Poisoner losing their ability, whichever is first** |
| **Pukka** | chosen | when the Pukka's next choice resolves (target dies, then becomes healthy) — "The Innkeeper prevents the Pukka from killing a poisoned player, then that player is no longer poisoned" — or the Pukka losing their ability |
| **Courtier** | the player of the chosen **character** (not a player!) | dusk after 3 nights & 3 days: choose on night N → drunk nights N..N+2 and days N..N+2, token removed at dusk before night N+3. Choosing a not-in-play character does nothing but is still spent |
| **Sailor** | self or chosen | dusk of that day |
| **Innkeeper** | 1 of the 2 chosen (**storyteller** picks) | dusk of that day. The drunk one is still protected — unless it is the Innkeeper, in which case *neither* is protected |
| **Sweetheart** | any 1 player (ST picks) | **forever**, and it survives the Sweetheart's own death (`endsWithSource = false`) |
| **Goon** | the **1st** player to choose the Goon that night, immediately, mid-ability | dusk of that day. Also flips the Goon's alignment to match |
| **Minstrel** | **all other players except Travellers** | dusk **of the following day** ("until dusk tomorrow") |
| **No Dashii** | nearest Townsfolk neighbour in each direction (skipping non-Townsfolk) | continuously re-derived; ends when the No Dashii dies/loses ability, or when seating/characters change |
| **Vigormortis** | 1 Townsfolk neighbour **per Minion it killed** | while the Vigormortis has its ability and that Minion is marked HAS ABILITY |
| **Snake Charmer** | the new Snake Charmer (the former Demon) | **forever/permanent** |
| **Widow** | chosen on the Widow's first night | while the Widow is alive **and sober** (explicitly reversible) |
| **Xaan** | **all Townsfolk** (by true character team — the Drunk is an Outsider and is *not* poisoned) | dusk of day X. X is frozen at setup |
| **Lleech** | the chosen host | wiki does not state an end condition; treat as "while the Lleech has its ability" and flag |
| **Philosopher** | the in-play player of the duplicated character | when the Philosopher dies **or becomes drunk/poisoned** |
| **Cannibal** | self, when the executee was evil | "until a good player dies by execution" (event-scoped) |
| **Barista** | chosen | **anti-impairment** until dusk; overrides existing DRUNK/POISONED tokens |
| **Puzzlemaster** | 1 player marked at setup | forever, **"even if you die"** (`endsWithSource = false`) |
| **Village Idiot** | 1 of the extra Village Idiots, marked at setup | forever; "If all sober Village Idiots exit play, the remaining drunk Village Idiot remains drunk"; never moves |
| **Marionette** | self | "It is just as if this player is the Drunk" — a permanent no-ability state |
| **Lunatic** | self | same (no ability, believes they are the Demon) |
| **Organ Grinder** | self, by choice each night | dusk |
| **Preacher** | chosen Minions | permanent NO ABILITY while the Preacher has their ability |
| **Boffin / Bone Collector** | — | *grant* ability rather than remove it; Bone Collector's HAS ABILITY expires at dusk |

---

## What the app does today

### The three disagreeing impairment predicates

There are **three** independent implementations of "is this player impaired", and they do not
agree:

1. `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) — `characterId == "drunk"`, or any
   reminder whose lowercased label **contains** `"poison"` or `"drunk"`, or membership in
   `derivedPoison`. Returns `Boolean`, with no reason.
2. `InfoCalc.impairments` (`InfoCalc.kt:133-153`) — a different scan producing prose: adds the
   Marionette (`:139-141`) and `label == "no ability"` (`:147`), the dead check (`:150`) and
   `derivedPoison` (`:151`).
3. `NightScreen.kt:904-906` — a scan of `InfoCalc`'s **prose caveats** for the substrings
   `"POISONED"`, `"DRUNK"`, `"IS the Drunk"`, `"VORTOX"`, `"No Dashii"`, used to decide whether to
   offer false-info chips.

Consumers: `GameActions.kill` snapshots `abilityImpairedAtDeath` from (1) (`GameActions.kt:153`);
`WinCheck` uses (1) for the Saint (`WinCheck.kt:58`); `GrimoireScreen.kt:332` badges the seat from
(1); `NightScreen.kt:548` warns that the Demon's attack fails from (1).

### `derivedPoison` (`StatusEffects.kt:14-33`)

Only rule implemented: an **alive** No Dashii poisons the nearest Townsfolk neighbour in each
direction. It does **not** check whether the No Dashii itself has an ability, does not offer
misregistration (a Recluse registering as Townsfolk), and de-duplicates silently when both
directions land on the same seat. It is the only derived effect in the codebase — Tea Lady
protection, Vigormortis poison, Xaan poison and Widow poison have no derivation at all.

### `deathNotes` (`StatusEffects.kt:52-129`) — prose, not outcomes

- Reminder scan `:64-71` with an **exact** lowercased match on four labels: `safe`, `protected`,
  `survives execution`, `can not die`.
- Standing protections `:73-78`: Sailor (`player.alive` only), Soldier (unconditional), Fool
  (checks the `No ability` mark), Lleech.
- Tea Lady `:79-91`.
- On-death triggers `:94-127`: 8 self-triggers by id, plus Scarlet Woman, Imp star-pass,
  Minstrel, Vigormortis, Godfather, Zombuul, Grandmother.

Every one of these is a `String` appended to a list. **Nothing consumes them programmatically.**

### `kill` / `revive` / `resurrect` / `DeathRecord`

`GameActions.kt:136-156` — `kill` checks only `player.alive`, flips `alive=false`, resets the ghost
vote and appends a `DeathRecord(playerId, day, atNight, cause, characterIdAtDeath,
abilityImpairedAtDeath)`. **No protection is consulted, no trigger fires, no reminder is placed or
cleared.** `revive` (`:162-166`) drops the last record; `resurrect` (`:173-181`) marks it
`resurrected = true`. Neither clears spent marks, `Dead` tokens, or re-queues first-night info.

`DeathCause` (`GameState.kt:75`) has five values: `EXECUTION, DEMON, OTHER_NIGHT_DEATH, EXILE,
STORYTELLER`. No source character, no source player.

### Expiry tables (`GameActions.kt:218-242`)

`EXPIRES_AT_DAWN`: `monk/Safe`, `innkeeper/Protected`, `exorcist/Chosen`, `lunatic/Attack 1..3`.
`EXPIRES_AT_DUSK`: `poisoner/Poisoned`, `sailor/Drunk`, `innkeeper/Drunk`, `butler/Master`,
`devilsadvocate/Survives execution`, `witch/Cursed`, `cerenovus/Mad`, `harpy/Mad`, `harpy/2nd`,
`goblin/Claimed`.

That is **10 of the ~40 timed tokens in the dataset**. A grep for the remaining labels
(`"Drunk 3"`, `"Everyone is drunk"`, `"Can not die"`, `"Died today"`, `"Has ability"`,
`"Sober & Healthy"`, `"3 attacks"`, `"Faux Paw"`, `"Lunch"`, `"About To Die"`, `"Protect"`) finds
**data files only** — no code touches any of them.

### The kill call sites

| Path | File:line | Consults protection? |
|---|---|---|
| Day tab, "On the block" banner → **Execute** | `DayScreen.kt:111-113` | **No** |
| Day tab, nomination row → **Execute / Exile** | `DayScreen.kt:350-357` | **No** |
| Dusk guard dialog → **Execute & begin night** | `GameShell.kt:596-604` | **No** |
| Seat sheet → Died at night / Executed / Other death | `SeatSheet.kt:266-287` | Warns, via a substring filter |
| Night sheet → Demon kill panel → "{name} dies" | `NightScreen.kt:625-633` | Prints notes, still kills |
| Imp star pass / Fang Gu jump | `GameActions.starPass`, `NightScreen.kt:591-622` | **No** |

`SeatSheet.kt:256-265` filters `deathNotes` for a *protection* subset by testing whether the prose
contains any of `"can't die"`, `"can not die"`, `"Safe"`, `"Protected"`, `"survives"`,
`"safe from"`, `"don't"`, `"Fool"`. If any match, a dialog appears whose two buttons are
"They die anyway" / "Death prevented" (`:288-307`) — the storyteller adjudicates, and choosing
"Death prevented" records **nothing at all**.

### What works

Ghost votes: `kill` grants a fresh ghost vote and `DayScreen.kt:233-240` spends it automatically on
executions but not on exiles — correct per the Glossary. `placeExclusiveReminder`
(`GameActions.kt:194-201`) correctly moves single-copy tokens, and `NightScreen.kt:319-337`
correctly recycles the oldest copy when a character has several. `resurrect` vs `revive` — keeping
the record for the Undertaker vs dropping it for an undo — is the right distinction.
`Player.nightRoleId` (`GameState.kt:39-44`) correctly wakes the Drunk and Marionette as the good
character they believe they are.

---

## Defects and gaps

### P0 — wrong outcome, storyteller misled, or rules broken

1. **P0 · No effect ends when its source loses their ability.**
   The single most-violated rule in the app (§B). A Poisoner who dies at night leaves their victim
   poisoned until dusk; a poisoned Widow keeps poisoning; a dead Philosopher keeps their target
   drunk; a drunk No Dashii keeps poisoning both neighbours (`StatusEffects.kt:17` checks only
   `it.alive`). Every info step for those seats then reports a caveat that the rules say should be
   gone. Repro: night 2, Poisoner poisons the Empath, then the Demon kills the Poisoner. Open the
   Empath step at night 3 — still "POISONED — give false info", and the token is still there
   through dusk.

2. **P0 · Every standing protection ignores its owner's impairment.**
   `StatusEffects.kt:73` Sailor (`player.alive` only), `:74` Soldier (unconditional), `:75` Fool,
   `:79-91` Tea Lady, and the Monk's `Safe`/Innkeeper's `Protected` tokens at `:66-67` (the token
   is read, its source's state never is). The rules are explicit: "The Poisoner poisons the
   Soldier, then the Imp attacks the Soldier. The Soldier dies, since they have no ability."
   Repro: poison the Sailor, then open the Demon panel and tap them — the app prints
   *"! The Sailor can't die."* and the seat-sheet dialog's dismiss button is labelled
   "Death prevented". The app is telling the storyteller to break the rules.

3. **P0 · No execution path consults protection at all.**
   Three of the six kill paths (`DayScreen.kt:111-113`, `DayScreen.kt:350-357`,
   `GameShell.kt:596-604`) call `viewModel.kill(id, EXECUTION)` with no `deathNotes` call anywhere
   in the file. A sober Sailor, a Tea Lady neighbour, a Devil's Advocate target, a Fool, a Vizier,
   a Zombuul and a Pacifist-eligible good player all just die from the Day tab. This is the path
   the storyteller actually uses at dusk.

4. **P0 · "Executed but did not die" is unrepresentable.**
   The only record of an execution is a `DeathRecord`, so an execution that kills nobody leaves no
   trace. Consequences, all wrong today: the Mayor's "no execution occurs" win cannot be computed
   (`WinCheck.kt` has only a *caution* string at `:91`); Vortox's "if no-one is executed, evil
   wins" likewise; a second nomination is not blocked; the Undertaker (`InfoCalc.kt:282-292`)
   cannot know it learns nothing; the Cannibal cannot know it gains nothing; the Minstrel cannot
   distinguish "Minion executed" from "Minion executed and died".

5. **P0 · `DeathCause.DEMON` is the UI's label for *any* night death.**
   `SeatSheet.kt:271-273` labels `DeathCause.DEMON` as **"Died at night"**, and there is no button
   producing `OTHER_NIGHT_DEATH` at all (the third button is `STORYTELLER`). So a Gossip kill, an
   Assassin kill, a Godfather kill, a Moonchild curse and a Gambler self-kill are all recorded as
   Demon kills — which is exactly the fact the Sage, Grandmother, Choirboy and Banshee key off
   ("Minions that kill the King, such as the Assassin, don't count either").

6. **P0 · The Minstrel's token marks the wrong player — inverted.**
   `characters.json:484` gives the Minstrel the seat token `"Everyone is drunk"`. Placed on the
   Minstrel's seat (or anywhere), `isImpaired` matches the substring `"drunk"` and reports **that
   one seat** as drunk. The rule is that **everyone except** that seat (and Travellers) is drunk.
   The app therefore produces the exact inverse of the ability, silently.

7. **P0 · Xaan's poison does not exist in the app.**
   No token in `characters.json` (only `Night 1/2/3` and `X`), no derivation, no `derivedPoison`
   rule. On night X every Townsfolk info step computes and displays **true** information with no
   warning. See `characters/xaan.md`.

8. **P0 · The Marionette and Lunatic are not impaired.**
   `StatusEffects.isImpaired` special-cases `characterId == "drunk"` only. `InfoCalc` knows about
   the Marionette (`InfoCalc.kt:139-141`) but `StatusEffects` does not, so the grimoire badge
   (`GrimoireScreen.kt:332`), `abilityImpairedAtDeath` and `WinCheck`'s Saint test all treat a
   Marionette as sober. Wiki: "It is just as if this player is the Drunk."

9. **P0 · Impairment cannot be cancelled.**
   The Barista's `Sober & Healthy` token (`characters.json`) is invisible to all three predicates,
   so a Barista-sobered player still shows as poisoned and still gets false-info chips. The wiki:
   "sober and healthy (**even if they're also marked DRUNK or POISONED**)".

10. **P0 · Scarlet Woman's 5-alive test counts Travellers.**
    `StatusEffects.kt:105` uses `seats.count { it.alive }`. Ability text: "(Travellers don't
    count.)" `GameState.aliveNonTravellers` (`GameState.kt:117`) exists and is used **nowhere**.

11. **P0 · Tea Lady uses raw seat adjacency, not alive neighbours.**
    `StatusEffects.kt:83-84` takes `(ti-1)` and `(ti+1)` literally. Glossary: "The two alive players
    that are sitting closest… **not including any dead players sitting between them**." Once one
    physical neighbour dies, the app protects the wrong seats and stops protecting the right ones.
    (Also duplicated in `characters/tealady.md` defect 1.)

12. **P0 · On-death triggers fire on the wrong condition.**
    `StatusEffects.kt:94-118`: `sage` fires on any death (must be a Demon-ability kill); `farmer`
    fires on any death (night only); `king`/Choirboy is unconditional (must be a Demon-ability kill
    **and** the Choirboy in play); `minstrel` fires on any Minion death (execution **and** death
    only, and only if the Minstrel is alive and sober); `vigormortis` fires on any Minion death
    (must be a kill by *that* Vigormortis); `godfather` fires on any Outsider death (day deaths
    only — "Outsiders that die at night don't count").

13. **P0 · Nothing represents "alive but registers as dead" (Zombuul).**
    `kill` early-returns on a dead player (`GameActions.kt:143`), so the Zombuul's real second
    death cannot be recorded once the first one has been entered, and entering the first death
    makes the Demon genuinely dead → `WinCheck` declares good the winner. See
    `characters/zombuul.md`.

14. **P0 · Impairment recursion is unmodelled, and the rules require it.**
    "if Julian poisons Amy, and then Evin poisons Julian, then Amy is no longer poisoned." Today a
    drunk Poisoner still poisons, a poisoned Monk still protects, and a drunk Sweetheart still
    drunks. There is no ordering, no cycle handling and no paradox escape hatch.

### P1 — storyteller must do bookkeeping the app could do

15. **P1 · 30 of ~40 timed tokens never expire.** `EXPIRES_AT_DUSK` is missing `goon/Drunk`,
    `organgrinder/Drunk`, `xaan/*`, `barista/Sober & Healthy`, `barista/Ability twice`,
    `bonecollector/Has ability`, `minstrel/Everyone is drunk` (dusk **tomorrow**),
    `cannibal/Poisoned` (event-scoped), `undertaker/Died today`, `godfather/Died today`,
    `zombuul/Died today`, `po/3 attacks`, `legion/About To Die`, `fearmonger/Fear`,
    `summoner/Night 1..3`. `EXPIRES_AT_DAWN` is missing `stormcatcher/Safe` (never — it is
    permanent, but `deathNotes` mislabels it), `lycanthrope/Faux Paw` (permanent).

16. **P1 · The Courtier's `Drunk 3 → 2 → 1` countdown is entirely manual.** Nothing decrements or
    removes it, and nothing places the Courtier's `No ability` at the end. The duration model has
    no "N nights & days" concept at all.

17. **P1 · No `impairment(player): List<Reason>` — the boolean loses the source.** Because
    `isImpaired` returns `Boolean`, no caller can say *why*, none can check whether the source is
    still valid, and none can offer a storyteller override. The seat badge, kill panels and Day
    screen therefore cannot show the actual state of the grimoire.

18. **P1 · Substring matching over English prose, three layers deep.**
    Failure modes present in the shipped dataset: false positives on `minstrel/"Everyone is drunk"`
    (defect 6) and on any homebrew label containing the letters (`"Drunkard"`, `"Poison Ivy"`, a
    token named `"Not drunk"`); false negatives on `preacher/"No Ability"`,
    `bonecollector/"No ability"`, `barista/"Sober & Healthy"`, `angel/"Protect"` (the exact-match
    scan at `StatusEffects.kt:65-70` wants `"protected"`), and every Xaan/Marionette/Lunatic case.
    `stormcatcher/"Safe"` matches the Monk's branch and is described as "protected from the Demon"
    when it actually means "can only die by execution".

19. **P1 · `deathNotes` conflates *blocks* with *warns*.** `SeatSheet.kt:256-265` tries to recover
    the distinction by string-matching the prose it just generated (`"don't"`, `"Fool"`,
    `"safe from"`). A deterministic block (sober Sailor), a might-block (Pacifist), a redirect
    (Mayor), a substitution (Scapegoat), a registration change (Zombuul) and an on-death trigger
    (Sage) are all the same `String` today.

20. **P1 · The Assassin's bypass is unexpressible, and the UI defaults against it.** With
    `deathNotes` warnings on screen, the seat-sheet dialog's dismiss button reads "Death
    prevented". An Assassin kill on a Monk-protected Soldier should be a plain `Dies`.

21. **P1 · Protections needing more than one token are capped at one.** `characters.json` gives the
    Tea Lady one `Can not die` (needs 2 — both neighbours), the Innkeeper one `Protected` (the wiki
    says "Mark **both** with SAFE reminders"), and the Vigormortis one `Poisoned` + one
    `Has ability` (needs one per killed Minion). `placeExclusiveReminder` then *moves* the first
    one when the second is placed, silently erasing it.

22. **P1 · Nothing is announced at dawn or at day start.** `NightOrder.kt:59` renders "Announce who
    died" as static prose; there is no computed list of tonight's deaths, no "no one died" line, no
    "X was executed but remains alive", no day-start briefing ("the Devil's Advocate protects Y
    today", "everyone is drunk until dusk", "the Fool's ability is spent"). A grep for a day-start
    briefing in `app/src` finds only `NotesScreen.kt:565` (player mode).

23. **P1 · Resurrection does not restore the ability or re-run first-night info.**
    `GameActions.resurrect` (`:173-181`) touches only `alive`, `ghostVoteUsed` and the record. Per
    the Glossary the player "gains their ability back, even if it was a 'once per game' ability
    that had been used. If this ability functions on the first night only, it functions tonight."
    So the Professor's target keeps their `No ability` mark, keeps every `Dead` token, and never
    gets their first-night step re-queued. **This is the user's reported bug.**

24. **P1 · `revive` (undo) cannot roll back a death's consequences.** Because side effects
    (Sweetheart drunk, Minstrel drunk, Scarlet Woman promotion, spent Fool mark, Vigormortis
    poison) are unlinked hand-placed tokens, undoing a mis-entered death leaves them behind.

25. **P1 · The Undertaker reads the *current* character, not the character at death.**
    `InfoCalc.kt:287-288` uses `ctx.character(player)`. `DeathRecord.characterIdAtDeath`
    (`GameState.kt:86`) exists precisely for this and is used only by `WinCheck`.

26. **P1 · Dead players cannot be nominated or executed.** `DayScreen.kt:146` requires `p.alive`.
    The rules say "even dead players may be executed again, just to be sure", and it is the *only*
    way for the town to kill a hiding Zombuul.

27. **P1 · Night steps have no wake condition.** `NightOrder.build` (`:142-178`) emits a row for
    every in-play character with a night reminder; `NightScreen.kt:749-757` shows "All holders are
    dead — usually skip" as advice. So a dead Monk is offered, a Vigormortis-preserved dead Minion
    is *discouraged*, and an impaired Poisoner is offered with no hint that the poison will not
    land.

28. **P1 · An exiled Traveller stays in the seat list with a ghost vote.** Exile removes a
    Traveller from the game; the app records `DeathCause.EXILE` and leaves them as an ordinary dead
    player who can spend a ghost vote on the next execution.

### P2 / P3

29. **P2 · Misregistration is never offered at a status decision point.** A Recluse registering as
    Townsfolk changes who the No Dashii poisons and who the Xaan poisons; a Spy registering as an
    Outsider arms the Godfather; a Recluse dying during the day may fail to. The app decides
    silently from raw `team`.
30. **P2 · No record of a survived death** — nothing appears in the log (`GameExtras.kt:50-62`)
    when a protection fires, so the storyteller cannot reconstruct "why is the Fool still alive".
31. **P2 · `nominationWarnings` (`StatusEffects.kt:132-166`) covers 4 of ~12 nomination-time
    triggers** — missing Golem's once-per-game mark, Fearmonger's "if you nominate & execute",
    Goblin's claim, Damsel, Riot, Vizier's immediate execution, Legion's "only evil voted",
    Boomdandy, Psychopath, Mutant madness, Witch's "if just 3 players live you lose this ability".
32. **P2 · `DeathCause.STORYTELLER` and `OTHER_NIGHT_DEATH` carry no source**, so the Angel Fabled
    ("whoever is most responsible") and the log can never name a cause.
33. **P3 · `derivedPoison` returns `Map<Long, String>`** — prose keyed by player id, so it cannot be
    merged with token-based effects or overridden.
34. **P3 · Two-seat circles** break the Tea Lady neighbour maths (`(ti-1) == (ti+1)`).

---

## Proposed behaviour (spec)

One engine file, `Status.kt`, replacing `StatusEffects.kt`; `GameActions.kill` becomes a thin
wrapper over `Status.killOutcome` + `Status.applyDeath`.

### 1. The Effect model

Replace the "tokens are the truth" design with "**effects are the truth; tokens are their
rendering**". `PlacedReminder` stays for purely decorative markers, but every rule-bearing token
becomes an `Effect`.

```kotlin
@Serializable
enum class EffectKind {
    // impairing
    DRUNK, POISONED, NO_ABILITY,
    // anti-impairing / ability-granting
    SOBER_HEALTHY,            // Barista — beats every impairment
    HAS_ABILITY,              // Bone Collector, Vigormortis-preserved Minion, Pixie
    // protective
    SAFE_FROM_DEMON,          // Monk SAFE, Soldier (innate)
    CANT_DIE_TONIGHT,         // Innkeeper SAFE
    CANT_DIE,                 // Sailor (innate, self), Tea Lady CANNOT DIE
    ONLY_EXECUTION_KILLS,     // Storm Catcher SAFE
    SURVIVES_EXECUTION,       // Devil's Advocate
    DAY_IMMUNE,               // Vizier (innate)
    DEATH_TIED_TO,            // Lleech -> host (targetId = the Lleech, linkedPlayerId = host)
    // state
    MAD, REGISTERS_AS, SPENT, MARKER,
}

@Serializable
enum class Until {
    DAWN,                 // "tonight"
    DUSK,                 // "until dusk" / "tonight and tomorrow day"
    DUSK_AFTER_N_DAYS,    // Minstrel (n = 1), Courtier (n = 2 -> 3 nights & 3 days)
    FOREVER,
    SOURCE_LOSES_ABILITY, // the default cap; also applied on top of every other value
    EVENT,                // untilEvent: "goodDiesByExecution", "pukkaNextChoice", "hostDies"
    MANUAL,
}

@Serializable
data class Effect(
    /** Monotonic. Doubles as the resolution-order key — see §2. */
    val id: Long,
    val kind: EffectKind,
    val targetId: Long,
    val linkedPlayerId: Long? = null,      // Lleech host, Grandmother grandchild
    val sourceCharacterId: String,         // "" = storyteller / house rule
    val sourcePlayerId: Long? = null,      // null = no living source to check
    val until: Until,
    val untilCycle: Int? = null,           // absolute cycle at which DUSK*/DAWN expiry fires
    val untilEvent: String? = null,
    /** False only for effects that explicitly outlive their source. */
    val endsWithSource: Boolean = true,
    /** Grimoire token text; "" renders no token (Soldier, Sailor, Vizier are innate). */
    val label: String = "",
    val note: String = "",                 // storyteller-visible explanation
    val createdCycle: Int,
    val createdAtNight: Boolean,
    /** The DeathEvent / night action that created it, for exact rollback. */
    val causeEventId: Long? = null,
    /** Storyteller override: keep the token, suppress the rule. */
    val suspended: Boolean = false,
)
```

`GameState` gains `effects: List<Effect>` and `nextEffectId: Long`. A save-migration reads existing
`PlacedReminder`s through a `TOKEN_TO_EFFECT` table keyed by `(sourceId, label)` so existing games
keep working.

**Innate effects** (Soldier, Sailor, Tea Lady, Fool, Mayor, Pacifist, Vizier, Zombuul, Puzzlemaster,
Village Idiot, Marionette, Lunatic, Drunk, No Dashii, Xaan, Widow, Lleech) are **derived**, not
stored: a table of `StandingRule`s is evaluated on every query so it tracks seating, character
changes and life status automatically.

```kotlin
data class StandingRule(
    val characterId: String,
    /** Emits the effects this character's mere presence creates, right now. */
    val emit: (state: GameState, holder: Player, lookup: (String) -> Character?) -> List<Effect>,
)
```

Derived effects get `id = holder.standingSince` — a new `Player.standingSince: Long` stamped with
the effect counter whenever a seat's `characterId` changes (0 at setup). That makes "the Poisoner
poisons the No Dashii on night 1" resolve correctly (the poison is later, so it wins) while a
Snake-Charmer-created No Dashii on night 4 starts fresh.

Rules to implement as `StandingRule`s (each already researched in the matching character audit):

| Character | Emits |
|---|---|
| `soldier` | `SAFE_FROM_DEMON` on self |
| `sailor` | `CANT_DIE` on self |
| `tealady` | `CANT_DIE` on each **alive** neighbour, only when both are good (registration-aware) |
| `vizier` | `DAY_IMMUNE` on self |
| `nodashii` | `POISONED` on the nearest Townsfolk neighbour each way |
| `vigormortis` | `POISONED` on 1 Townsfolk neighbour of each seat marked `HAS_ABILITY` by this Vigormortis |
| `widow` | keeps the stored `POISONED` alive only while the Widow has an ability (falls out of §2 for free) |
| `xaan` | on cycle == X, `POISONED` on every Townsfolk, `until = DUSK` |
| `lleech` | `DEATH_TIED_TO` on self, linked to the host |
| `drunk`, `marionette`, `lunatic` | `NO_ABILITY` on self, `endsWithSource = false` |
| `puzzlemaster`, `villageidiot` | nothing (their DRUNK is stored with `endsWithSource = false`) |
| `stormcatcher` (fabled) | `ONLY_EXECUTION_KILLS` on the named character's seat |

### 2. `impairment(player): List<Reason>` — with the recursion the rules require

```kotlin
data class Reason(val effect: Effect, val text: String)   // "Poisoned by the Poisoner (Ana)"

object Status {
    fun impairment(state: GameState, lookup: (String) -> Character?, playerId: Long): List<Reason>
    fun isImpaired(...) = impairment(...).isNotEmpty()
    fun hasAbility(state, lookup, playerId): Boolean
    fun protections(state, lookup, playerId): List<Effect>
    fun effectsOn(state, lookup, playerId): List<Effect>   // stored + derived, expiry applied
}
```

Algorithm — `abilityWorks(pid, cap)`, memoised on `(pid, cap)`:

```
1. p = player(pid); c = character(p)
2. if (!p.alive && !keepsAbilityWhenDead(c) && no active HAS_ABILITY on p) return false
3. actives = effectsOn(pid).filter { it.id < cap && !it.suspended && !expired(it, state) }
4. if (actives.any { it.kind == SOBER_HEALTHY && active(it, cap) }) return true      // Barista wins outright
5. return actives.none { it.kind in IMPAIRING && active(it, cap) }

active(e, cap) =
    !expired(e, state) && !e.suspended &&
    (!e.endsWithSource || e.sourcePlayerId == null || abilityWorks(e.sourcePlayerId, min(cap, e.id)))
```

`impairment(pid) = effectsOn(pid).filter { it.kind in IMPAIRING && active(it, Long.MAX_VALUE) }`
mapped to `Reason`s, unless a `SOBER_HEALTHY` effect is active, in which case it returns
`emptyList()` and a single explanatory `Reason` of kind `SOBER_HEALTHY` for display.

- **Termination**: `cap` strictly decreases on every recursive step (`min(cap, e.id)` with
  `e.id < cap`), so the recursion is bounded by the number of effects. Memoise per query.
- **`keepsAbilityWhenDead`**: a small id set — `recluse`, `spy`, `ravenkeeper`, `sweetheart`,
  `moonchild`, `klutz`, `barber`, `hatter`, `poppygrower`, `plaguedoctor`, `heretic`, `atheist`,
  `politician`, `banshee`, `zealot`, `puzzlemaster`, plus anything holding a live `HAS_ABILITY`
  effect. (Wiki: "This ability is still lost if the player becomes drunk or poisoned.")
- **Paradox**: if evaluation ever needs an effect with `id == cap` (two effects stamped the same
  sequence, only possible for two derived effects created in the same swap), mark the query
  `paradox = true`, resolve as "both active", and surface a storyteller prompt: *"Paradox: A and B
  poison each other. Tap the one whose ability works."* — persisted as an `Effect.suspended` flag.

**Consumers**, all switched to this one function: `InfoCalc.impairments` (delete its private scan
and render `Reason.text`), `NightScreen`'s false-info chips (test `impairment().isNotEmpty()`, not
prose), `GrimoireScreen`'s seat badge (show the reason on tap), `GameActions.kill`'s
`abilityImpairedAtDeath`, `WinCheck`'s Saint test, and every night step's wake condition
(§5).

### 3. Protection: `killOutcome(...)`

```kotlin
@Serializable
enum class DeathKind {
    DEMON_ABILITY,   // any Demon's own ability — kill or non-kill harm
    EVIL_ABILITY,    // Assassin, Godfather, Witch, Psychopath, Boomdandy, Fearmonger…
    GOOD_ABILITY,    // Gossip, Lycanthrope, Slayer, Golem, Moonchild, Gambler, Tinker, Harlot…
    EXECUTION,
    EXILE,
    STORYTELLER,
}

@Serializable
data class KillCause(
    val kind: DeathKind,
    val sourceCharacterId: String? = null,
    val sourcePlayerId: Long? = null,
    /** Assassin only. */
    val ignoresProtection: Boolean = false,
)

sealed interface KillOutcome {
    /** Nothing stops it. [events] is the death plus everything it triggers. */
    data class Dies(val events: List<DeathEvent>) : KillOutcome
    /** Deterministic block. [announce] is the exact line to say out loud. */
    data class Blocked(val by: Effect?, val reason: String, val announce: String) : KillOutcome
    /** The Zombuul's first death: alive, registers as dead. */
    data class RegistersDead(val reason: String) : KillOutcome
    /** Mayor bounce, Scapegoat substitution: the death moves. */
    data class Redirect(val to: List<Long>, val reason: String, val mandatory: Boolean) : KillOutcome
    /** "might" abilities — Pacifist, Mayor, Scapegoat, Deviant: the ST decides, every time. */
    data class Choice(val question: String, val options: List<Labeled<KillOutcome>>) : KillOutcome
    /** The Fool: wraps a Blocked and spends the ability. */
    data class Spends(val inner: KillOutcome, val mark: Effect) : KillOutcome
    /** "A dead player cannot die again." Still counts as the day's execution. */
    object AlreadyDead : KillOutcome
}

fun killOutcome(
    state: GameState,
    lookup: (String) -> Character?,
    targetId: Long,
    cause: KillCause,
): KillOutcome
```

Resolution order — first match wins; every protection is only considered when
`Status.hasAbility(its source)` (which §2 gives for free, because protections are `Effect`s whose
`endsWithSource` is true):

```
 0. target not alive and not a registers-dead Zombuul       -> AlreadyDead
 1. cause.ignoresProtection                                 -> Dies        (Assassin)
 2. DEATH_TIED_TO with a living linked host                 -> Blocked     (Lleech)
 3. DAY_IMMUNE and !atNight                                 -> Blocked     (Vizier)
 4. ONLY_EXECUTION_KILLS and kind != EXECUTION              -> Blocked     (Storm Catcher)
 5. CANT_DIE                                                -> Blocked     (Sailor, Tea Lady)
 6. CANT_DIE_TONIGHT and atNight                            -> Blocked     (Innkeeper)
 7. SAFE_FROM_DEMON and kind == DEMON_ABILITY               -> Blocked     (Monk, Soldier)
 8. SURVIVES_EXECUTION and kind == EXECUTION                -> Blocked     (Devil's Advocate)
 9. kind == EXECUTION, target registers good, a Pacifist
    has their ability                                       -> Choice      (dies / lives)
10. kind == EXILE, target is a Deviant marked funny         -> Choice
11. atNight, kind != EXECUTION, target is a Mayor with
    their ability                                           -> Choice(dies / Redirect(others))
12. kind == EXECUTION, a Scapegoat of the target's
    registered alignment is alive with their ability        -> Choice(dies / Redirect(scapegoat))
13. target is a Zombuul with no prior death and has
    their ability                                           -> RegistersDead
14. target is a Fool with no SPENT effect and has
    their ability                                           -> Spends(Blocked)
15. otherwise                                               -> Dies(applyDeath(...))
```

Notes on order, each rules-backed: the Fool is **last** so other protections take precedence and it
is not consumed; the Assassin is **first** so nothing else is even evaluated; the Mayor is *after*
the blocks so a Monk-protected Mayor produces "nobody dies", not a redirect. `Choice` outcomes must
render as buttons, never as advice — "might" is a storyteller decision the app should *ask*, not
*explain*.

`DEMON_ABILITY` also gates non-kill harm, so the same function answers "is the Soldier poisoned by
the No Dashii?" — the standing rule calls `killOutcome`-style protection lookup before emitting
`POISONED`. Concretely add:

```kotlin
fun demonHarmBlocked(state, lookup, targetId): Boolean   // SAFE_FROM_DEMON active
```
used by the No Dashii, Vigormortis, Fang Gu, Pukka and Lord of Typhon standing rules. This fixes
`characters/soldier.md` defect 3 in one place.

**Cause table** (`DEMON_ABILITY` unless noted) — used to stamp `KillCause` automatically from the
night step that produced the kill, so the storyteller never picks a cause by hand:

```
imp, pukka, po, shabaloth, fanggu, nodashii, vortox, vigormortis, zombuul, lleech, kazali,
lordoftyphon, ojo, alhadikhia, yaggababble, lilmonsta, legion, riot   -> DEMON_ABILITY
assassin(ignoresProtection=true), godfather, witch, psychopath, boomdandy, fearmonger,
  mezepheles, harpy                                                   -> EVIL_ABILITY
gossip, lycanthrope, slayer, golem, moonchild, gambler, tinker, harlot,
  virgin, judge, doomsayer                                            -> GOOD_ABILITY
(execution / exile / storyteller)                                     -> EXECUTION / EXILE / STORYTELLER
```
`lilmonsta`, `legion`, `riot`, `yaggababble`, `alhadikhia` carry a `demonKillUncertain = true` flag:
the kill panel shows a single toggle *"Counts as a Demon kill (Sage / Grandmother / Choirboy)?"*
defaulting to yes, because the wiki does not rule on it.

### 4. `ExecutionRecord` — making "executed but did not die" real

```kotlin
@Serializable
enum class ExecutionKind { VOTE, VIRGIN, VIZIER, JUDGE, SCAPEGOAT_SUBSTITUTE, RIOT }

@Serializable
data class ExecutionRecord(
    val day: Int,
    val nomineeId: Long,
    val kind: ExecutionKind,
    val nominationIndex: Int? = null,
    /** Null = "executed but did not die". */
    val diedEventId: Long? = null,
    /** "devilsadvocate" | "pacifist" | "sailor" | "tealady" | "fool" | "vizier" |
     *  "zombuul" | "alreadyDead" | "scapegoat" */
    val preventedBy: String? = null,
)
```

`GameState.executions: List<ExecutionRecord>`, plus derivations:

```kotlin
fun executionToday(state: GameState): ExecutionRecord?          // at most one; blocks further nominations
fun executionDeathToday(state: GameState): DeathEvent?          // null when nobody died
fun dayDeathsToday(state: GameState): List<DeathEvent>          // Zombuul / Godfather gates
```

Rewire: Undertaker (`InfoCalc.kt:282-292`) → `executionDeathToday`, and read
`DeathEvent.characterIdAtDeath`, not the live seat. Cannibal → same record. Minstrel / Mastermind /
Saint / Evil Twin / Goblin → `executionDeathToday` + team/character at death. Godfather →
`dayDeathsToday().any { teamAtDeath == OUTSIDER }`. Zombuul → `dayDeathsToday().isNotEmpty()`.
Mayor and Vortox → `executionToday() != null` (**execution**, not death).

Also: `DayScreen` must allow nominating a dead player (drop the `p.alive` filter at `:146`,
keep it for the *nominator*), and the Execute buttons at `:111-113`, `:350-357` and
`GameShell.kt:596-604` must all route through `killOutcome` and write an `ExecutionRecord`
whatever the outcome.

### 5. `DeathEvent` and the trigger system

```kotlin
@Serializable
data class DeathEvent(
    val id: Long,
    val playerId: Long,
    val characterIdAtDeath: String?,
    val teamAtDeath: Team?,
    val evilAtDeath: Boolean,
    val cause: KillCause,
    val cycle: Int,
    val atNight: Boolean,
    val impairedAtDeath: Boolean,
    /** Zombuul: alive, but registers as dead everywhere. */
    val registeredOnly: Boolean = false,
    val resurrectedAtCycle: Int? = null,
)

@Serializable
enum class PromptWhen { NOW, DAWN, DAY_START, TONIGHT, NOMINATION, EXECUTION, DUSK }

@Serializable
data class Prompt(
    val id: Long,
    val causeEventId: Long?,        // rollback key
    val at: PromptWhen,
    val characterId: String,
    val subjectPlayerId: Long? = null,
    val title: String,              // imperative, storyteller voice
    val kind: PromptKind,           // ANNOUNCE | CHOOSE_PLAYER | CHOOSE_CHARACTER | PLACE_EFFECT |
                                    // RESOLVE_KILL | RUN_FIRST_NIGHT | INFO
    val options: List<Long> = emptyList(),
    val resolved: Boolean = false,
)
```

`GameState.prompts: List<Prompt>`. `NightScreen` inserts `TONIGHT` prompts as **dynamic night
steps** at the night-order position of `Prompt.characterId`; a new dawn sheet renders `DAWN`;
the Day tab header renders `DAY_START`; `DayScreen` renders `NOMINATION`/`EXECUTION` inline.

```kotlin
data class DeathTrigger(
    val characterId: String,
    val matches: (state: GameState, event: DeathEvent, holder: Player) -> Boolean,
    val produce: (state: GameState, event: DeathEvent, holder: Player) -> Pair<List<Prompt>, List<Effect>>,
)

fun onDeath(state: GameState, lookup: (String) -> Character?, event: DeathEvent): GameState
```
Every trigger's `matches` must additionally require `Status.hasAbility(holder)` unless the
character's text says "even if dead".

**The complete trigger table** (condition → what the app must do). Grouped by trigger condition;
`✗` marks the ones `deathNotes` has today, `≠` marks the ones it has with the **wrong** condition.

*The Demon's ability kills this player:*
| Character | Fires |
|---|---|
| `sage` ≠ | TONIGHT: wake, show 2 players, one the Demon |
| `banshee` | DAWN: announce publicly; grant `nominate ×2 / vote ×2` from now on |

*The Demon's ability kills **another** player:*
| `grandmother` ✗ | the grandchild dies → the Grandmother dies too (a second `kill`, cause `GOOD_ABILITY`/grandmother) |
| `choirboy` ≠ | the **King** dies → TONIGHT: wake the Choirboy, show the Demon |

*Any night death of this player:*
| `ravenkeeper` ≠ | TONIGHT: wake, choose a player, learn their character |
| `farmer` ≠ | TONIGHT: choose a living good player → becomes a Farmer |
| `mayor` | handled in `killOutcome` step 11 (redirect), not here |

*Any death of this player:*
| `sweetheart` ✗ | TONIGHT: choose 1 player → `DRUNK`, `FOREVER`, `endsWithSource = false` |
| `barber` ✗ | TONIGHT: the Demon may swap 2 players' characters |
| `hatter` | TONIGHT: Minions & Demon may pick new characters |
| `poppygrower` ✗ | TONIGHT: Minions & Demon learn each other |
| `klutz` | DAY_START (or NOW if a day death): publicly choose 1 alive player; evil → good loses |
| `moonchild` ✗ | NOW: publicly choose 1 alive player → TONIGHT: if good, they die |
| `plaguedoctor` | NOW: the storyteller gains a Minion ability |
| `pixie` (other) | the character they were mad about dies → the Pixie gains that ability |
| `bountyhunter` (other) | the known evil player dies → TONIGHT: learn another evil player |
| `scarletwoman` ✗ | the Demon dies with **≥5 alive non-Travellers** → the Scarlet Woman becomes that Demon |
| `imp` ✗ | the Imp self-kills → NOW: choose the heir Minion (star pass) |
| `fanggu` | the 1st Outsider it kills → that Outsider becomes an evil Fang Gu, the Fang Gu dies |
| `vigormortis` ≠ | a Minion **this Vigormortis killed** → `HAS_ABILITY` on the Minion + `POISONED` on 1 TF neighbour |
| `lilmonsta` | the babysitter dies → good wins |
| `lleech` | the host dies → the Lleech dies, good wins |
| `angel` (fabled) | a `PROTECT`-marked new player dies → NOW: "choose something bad for whoever is most responsible" |
| `shabaloth` | a player it chose **last night** → TONIGHT: may regurgitate (resurrect) |
| `professor`, `bonecollector` | act on dead players (existing resolvers) |

*Day death of an Outsider:*
| `godfather` ≠ | arm tonight's kill; TONIGHT: choose a player |

*Any day death:*
| `zombuul` ≠ | mark `DIED TODAY` → the Zombuul does **not** wake tonight |

*Death **by execution** (i.e. `ExecutionRecord.diedEventId != null`):*
| `undertaker` | TONIGHT: show `characterIdAtDeath` |
| `cannibal` | NOW: `LUNCH` on the dead player; the Cannibal gains that ability; if the dead player registered evil, `POISONED` on the Cannibal `until = EVENT("goodDiesByExecution")` |
| `minstrel` ≠ | dead player was a Minion → `DRUNK` on **all other non-Traveller players**, `until = DUSK_AFTER_N_DAYS(1)`; DAY_START briefing tomorrow |
| `saint` | good loses |
| `mastermind` | the Demon died by execution → play one more day |
| `eviltwin` | the good twin died → evil wins |
| `goblin` | claimed when nominated → evil wins |
| `leviathan` | count good executions; >1 → evil wins |
| `boomdandy` | the Boomdandy died → all but 3 die, run the countdown |
| `princess` | the Princess nominated & executed on day 1 → the Demon doesn't kill tonight |
| `psychopath` | roshambo prompt before the death resolves |

*Impairment-triggered (not a death):*
| `acrobat` | their chosen player is or becomes impaired tonight → the Acrobat dies. Needs an `onImpairmentChanged` hook fed by the same effect engine |

`onDeath` returns the new state with `prompts` and `effects` appended, each stamped
`causeEventId = event.id`.

### 6. Death records and resurrection

Replace `DeathRecord` with `DeathEvent` (§5); keep a deserialiser that maps the old five-value
`DeathCause` onto `KillCause` (`DEMON → DEMON_ABILITY`, `OTHER_NIGHT_DEATH → STORYTELLER` with a
migration note, etc.).

```kotlin
/** The player lives again. Rules: they regain their ability, even a spent once-per-game. */
fun resurrect(state: GameState, playerId: Long, lookup: (String) -> Character?): GameState {
    // 1. alive = true, ghostVoteUsed = false
    // 2. mark the newest un-resurrected DeathEvent resurrectedAtCycle = cycle (keep it: the
    //    Undertaker already learned it, the Zombuul's DIED TODAY was a fact about that day)
    // 3. drop every SPENT effect on this seat, and every effect this seat's own death created
    //    (effects with causeEventId == thatEvent.id)
    // 4. drop Dead-family markers on this seat
    // 5. queue Prompt(at = TONIGHT, kind = RUN_FIRST_NIGHT, subjectPlayerId = playerId,
    //      title = "<name> is alive again — run their FIRST-NIGHT step tonight")
    // 6. queue Prompt(at = DAWN, kind = ANNOUNCE, title = "Announce that <name> is alive again")
}

/** Undo a mis-entered death: roll back everything it caused. */
fun revive(state: GameState, playerId: Long): GameState {
    // drop the newest DeathEvent for this player, and every Effect / Prompt whose
    // causeEventId matches it; alive = true, ghostVoteUsed restored to its pre-death value
}
```

`revive` needs the pre-death ghost-vote value, so `DeathEvent` gains
`ghostVoteUsedBeforeDeath: Boolean`.

Travellers and ghost votes:
- Exile writes a `DeathEvent(cause = EXILE)` **and** sets `Player.leftGame = true`, which removes
  the seat from `alivePlayers`, from both thresholds, and from the vote chip row. No ghost vote.
- Dead players may support an exile without spending a ghost vote (`DayScreen.kt:233-240` already
  correct — keep it).
- A Zombuul with a `registeredOnly` death has `alive = true` but `registersDead = true`; every
  alive-count (`alivePlayers`, `executionThreshold`, Empath, Chambermaid, Tea Lady neighbours,
  Godfather, nomination eligibility) must read a new `Player.registersAlive` derived property
  rather than `alive`.
- Ferryman (Fabled): on the final day, clear `ghostVoteUsed` for every dead player.

### 7. UI text the model should emit

Short, imperative, storyteller voice. These are the strings `killOutcome` and the prompt queue
should produce, and they should appear as *buttons and banners*, not advice:

- Blocked (deterministic): **"Nobody dies — the Monk protected Ana."** / at an execution:
  **"Say: 'Ana was executed… and remains alive.' Do not say why."**
- `Choice` (Pacifist): **"Ana is good and was executed. Do they die?"** → `[They die]`
  `[They survive — say nothing]`
- `Choice` (Mayor): **"The Mayor would die tonight. Who dies instead?"** → seat chips +
  `[The Mayor dies]`
- `RegistersDead`: **"Declare that the Zombuul died — but do not shroud them. They register as
  dead from now on."**
- `Spends` (Fool): **"Ana doesn't die — the Fool's ability is now spent."**
- Dawn: **"Announce: Ben and Cara died."** / **"Announce: nobody died."** /
  **"Announce: Ana is alive again."**
- Day start: **"Today: Dana survives execution (Devil's Advocate). Everyone except Ari is drunk
  until dusk (Minstrel). The Fool's ability is spent."**
- Seat badge tap: **"Poisoned by the Poisoner (Ari) — until dusk, or until Ari loses their
  ability."**

### 8. Data changes

`characters.json`:
- `minstrel`: move `"Everyone is drunk"` from `reminders` to `remindersGlobal` (it is a
  grimoire-centre token, not a seat token) — and it must never be read as an impairment on the seat
  it sits on.
- `tealady`: two `"Can not die"` entries. `innkeeper`: two `"Protected"` (wiki says mark **both**).
- `vigormortis`: three `"Poisoned"` and three `"Has ability"` (one per possible Minion).
- `xaan`: add `"Poisoned"`.
- `stormcatcher`: rename its `"Safe"` to `"Only execution kills"` (or keep the label and rely on
  `sourceCharacterId`, which the effect model does anyway — but the label is what the ST reads).
- `angel`: `"Protect"` → `"Protected"` for consistency, or map it in `TOKEN_TO_EFFECT`.

`night_and_jinxes.json`: add the missing protection-relevant jinxes surfaced by the character
audits — Monk↔Leviathan, Monk↔Riot, Soldier↔Leviathan, Soldier↔Riot, Innkeeper↔Riot, Mayor↔Riot,
Vizier's nine, Minstrel↔Legion, Vigormortis↔Mastermind, Widow↔Alchemist.

No night-order changes are needed for this scope; dynamic steps are inserted by `Prompt.characterId`
at that character's existing position.

---

## Tests to add

Engine tests, all of which fail today.

**Effect lifetime and recursion**

1. *Given* a Poisoner poisons the Empath on night 2, *when* the Poisoner is killed later that same
   night, *then* `impairment(empath)` is empty and the `Poisoned` token is gone.
   (Wiki: Abilities — the explicit Poisoner/Slayer example.)
2. *Given* the Widow poisons the Empath on night 1, *when* the Innkeeper makes the Widow drunk on
   night 3, *then* the Empath is healthy; *when* the Innkeeper dies, *then* the Empath is poisoned
   again. (Wiki: Widow example, verbatim.)
3. *Given* a Philosopher duplicating the in-play Artist, *when* the Philosopher dies, *then* the
   Artist is sober.
4. *Given* Julian poisons Amy and then Evin poisons Julian, *then* Amy is not impaired and Julian
   is. *And* `impairment(amy)` returns the *reason* naming Julian.
5. *Given* a drunk Poisoner, *when* they poison the Chef, *then* the Chef is not impaired
   (the token is still placed).
6. *Given* two players who poison each other with equal effect ids, *then* the query reports
   `paradox` and does not stack-overflow.
7. *Given* a No Dashii whose Townsfolk neighbour is the Soldier, *then* the Soldier is **not**
   poisoned (`SAFE_FROM_DEMON` blocks all harmful Demon-ability effects).
8. *Given* a drunk No Dashii, *then* neither neighbour is poisoned.
9. *Given* a Barista marks the poisoned Empath `Sober & Healthy`, *then* `impairment(empath)` is
   empty; *at dusk*, it is non-empty again.
10. *Given* the Puzzlemaster's drunk player, *when* the Puzzlemaster dies, *then* that player is
    still drunk (`endsWithSource = false`).
11. *Given* a Sweetheart made someone drunk on death, *then* that drunkenness survives the
    Sweetheart being dead and any later impairment of the Sweetheart.
12. *Given* the Courtier chooses the Imp on night 1, *then* the Imp is impaired on nights 1–3 and
    days 1–3, and sober from dusk of day 3; and the Courtier holds `No ability` after.
13. *Given* a Minstrel and an executed Minion who died on day 2, *then* every non-Traveller except
    the Minstrel is impaired through day 3, and sober at dusk of day 3. *And* the Minstrel is not
    impaired.
14. *Given* a Minion executed but saved by the Devil's Advocate, *then* the Minstrel does **not**
    fire. *And* given the Minstrel is poisoned when the Minion dies, it does not fire.
15. *Given* Xaan with X = 2, *then* on night 2 every **Townsfolk** is poisoned and the Drunk (an
    Outsider) is not; the poison is gone at dusk of day 2; a dead Xaan poisons nobody.
16. *Given* a Marionette shown as the Empath, *then* `isImpaired(marionette)` is true and
    `abilityImpairedAtDeath` on their death record is true.

**Protection**

17. Sober Sailor + `EXECUTION` → `Blocked`, and an `ExecutionRecord` with `diedEventId = null`,
    `preventedBy = "sailor"`. Poisoned Sailor + `EXECUTION` → `Dies`.
18. Soldier + `DEMON_ABILITY` → `Blocked`; Soldier + `EXECUTION` → `Dies`; Soldier +
    `EVIL_ABILITY`(godfather) → `Dies`.
19. Monk-protected Imp self-kill → `Blocked`, **no star pass**, no new Imp.
20. Monk-protected Mayor + `DEMON_ABILITY` → `Blocked` with **no** redirect choice offered.
21. Assassin on a Monk-protected, Innkeeper-protected, Tea-Lady-protected Fool → `Dies`, and the
    Fool's ability is **not** spent.
22. Drunk Assassin → the once-per-game is spent and nobody dies.
23. Innkeeper protection blocks a Godfather kill at night, and does **not** block an execution.
24. Tea Lady with a dead physical neighbour protects the next **alive** neighbour, not the corpse;
    a poisoned Tea Lady protects nobody; with a two-seat circle no exception is thrown.
25. Fool executed on day 1 → `Spends(Blocked)` and a `SPENT` effect; executed again on day 4 →
    `Dies`.
26. Devil's Advocate token expires at dusk and cannot be placed on the same player two nights
    running (constraint check available to the picker).
27. Storm Catcher's marked player: `EXECUTION` → `Dies`; every other cause → `Blocked`.
28. Vizier executed → `Blocked`; Vizier killed at night → `Dies`.
29. Zombuul's first death → `RegistersDead`, `alive == true`, `registersAlive == false`,
    `WinCheck` reports **no** good win; second death → `Dies` and good wins. A **poisoned**
    Zombuul's first death → `Dies`.
30. Lleech: executed while the host lives → `Blocked`; host dies → the Lleech dies and good wins.
31. Pacifist: executing a good player returns a `Choice`; executing an evil player does not;
    a poisoned Pacifist offers no choice; an exiled good Traveller offers no choice.
32. Scapegoat: executing a good player with an alive good Scapegoat offers a `Choice`; taking it
    kills the Scapegoat, leaves the nominee alive, and still writes one `ExecutionRecord` for the
    day.

**Execution and death records**

33. An execution that kills nobody still blocks further nominations that day, and makes the Mayor's
    3-alive win **not** apply.
34. Undertaker after a bloodless execution learns nothing; after a real one, learns
    `characterIdAtDeath` even if the seat's character changed afterwards.
35. Cannibal gains nothing from a bloodless execution and nothing from executing an already-dead
    player.
36. Godfather is armed by an Outsider dying **during the day** and not by one dying at night, and
    not by an Outsider executed who did not die.
37. Zombuul's night step is suppressed when any player died during the day, including when the
    Zombuul itself "died" by execution; executing an already-dead player does **not** suppress it.
38. A dead player can be nominated and executed; it counts as the day's execution;
    `KillOutcome.AlreadyDead`.
39. Sage fires on an Imp kill, not on an execution, not on a Pit-Hag arbitrary death, not on an
    Assassin kill.
40. Grandmother dies when the grandchild is killed by the Demon's ability; does not when the
    grandchild is executed or killed by the Godfather.
41. Choirboy fires only when the Demon's ability kills the King (not an Assassin kill, not an
    execution).
42. Ravenkeeper fires on **any** night death, including a Godfather kill; Farmer fires on any night
    death but not on an execution.
43. Scarlet Woman: the Demon dies with 5 alive **non-Travellers** → promotion; with 5 alive of
    which 1 is a Traveller → no promotion.

**Resurrection**

44. Professor resurrects a Ravenkeeper: `alive == true`, the death record stays marked resurrected,
    every `SPENT` mark on that seat is cleared, and a `RUN_FIRST_NIGHT` prompt exists for tonight.
45. Resurrecting a Slayer who had used their once-per-game restores the ability (Glossary:
    "even if it was a 'once per game' ability that had been used").
46. `revive` after a Sweetheart death removes the Sweetheart-created `DRUNK` effect and its prompt;
    `resurrect` after the same death does **not** (they really did die).
47. Exiling a Traveller removes them from `alivePlayers`, from both thresholds and from the vote
    row, and grants no ghost vote; a Tea-Lady-protected Traveller is exiled but remains alive.
