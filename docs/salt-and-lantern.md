# Salt & Lantern
### A Blood on the Clocktower script · 13 Townsfolk / 4 Outsiders / 4 Minions / 4 Demons · 12 homebrew characters

*Ravenswood Bluff has a harbour now, and a harbour needs a light. On calm nights the lantern keeps ships off the rocks. On other nights, somebody moves the lantern.*

v1.2 — Storyteller's edition (v1.2: Ferryman must directly choose another dead player; a new Wrecker jinx allows the choice to redirect back to the Ferryman. This is the only character change from v1.1. In v1.1, Devil's Advocate replaced the Press-Gang and the 9- and 15-player bags were adjusted). Contents: the pitch, the character sheet, rulings & jinxes, night order, three bags with full reasoning, how to run each homebrew, playbooks for both teams, and design notes (including what I cut and why). A `salt-and-lantern.json` sits alongside this file for the script tool / town-square apps.

---

## 1. The pitch

Most scripts corrupt information by making it **false** (Poisoner, Drunk, Vortox). Salt & Lantern mostly corrupts it by making it **displaced**: true, but about the wrong seat, the wrong body, or the wrong night. The question the town has to learn to ask isn't "is this reading true?" but "what is it true *about*?"

Four pillars:

1. **Signals and displacement.** Three positional readings (Chef, Empath, Cartographer) plus a one-shot rangefinder (Navigator) are the town's engine. Two things bend them: the Stowaway, whose seat is invisible to seat-counting abilities, and the Wrecker, who swaps the targets of good "choose a player" abilities without anyone being told.
2. **Public lights.** The Lighthouse Keeper's protection is announced every dawn. The Harbourmaster closes the harbour out loud. The Slayer, Virgin and Albatross fire in daylight, and the Devil's Advocate is the one Minion whose moment is a day the town thought it had won. Every day has an event; evil's minions are quiet, so the day game is driven by the town's own tools.
3. **The sea keeps count.** Bodies are evidence. The Pearl Diver reads the drowned, the Ferryman sends one back, the Ravenkeeper speaks from the water, the Imp's starpass leaves a corpse the Diver can read, and the Siren's conversion leaves *no* corpse at all — which is its own clue.
4. **A misinformation ladder.** The first bag uses only the Wrecker and the Drunk, so almost every reading is true about *someone*. Later bags add the Poisoner and the Pukka's night-one poison for tables that want fog.

### All 25 at a glance (★ = homebrew)

| Townsfolk | Outsiders | Minions | Demons |
|---|---|---|---|
| Chef | Drunk | Poisoner | Imp |
| Investigator | Recluse | ★ Wrecker | Pukka |
| Empath | ★ Stowaway | ★ Smuggler | ★ Siren |
| Fortune Teller | ★ Albatross | Devil's Advocate | ★ Kraken |
| Slayer | | | |
| Virgin | | | |
| Ravenkeeper | | | |
| ★ Cartographer | | | |
| ★ Lighthouse Keeper | | | |
| ★ Harbourmaster | | | |
| ★ Pearl Diver | | | |
| ★ Navigator | | | |
| ★ Ferryman | | | |

No character on this script changes the Outsider count. Setup is exactly the base numbers, which keeps night-one seat maths honest — and seat maths is the star.

---

## 2. Character sheet

"Each night\*" means every night except the first. Official characters use their official text; the summaries here are for reference.

### Townsfolk

| Character | Ability | Storyteller notes |
|---|---|---|
| **Chef** | You start knowing how many pairs of evil players sit next to each other. | Skip the Stowaway's seat: two evils separated only by the Stowaway are a pair. Recluse may count. |
| **Investigator** | You start knowing that 1 of 2 players is a particular Minion. | The natural counter to a hidden Wrecker/Smuggler. Recluse may register as a Minion. |
| **Empath** | Each night, you learn how many of your 2 alive neighbours are evil. | Skip the Stowaway's seat. A Siren-converted Outsider counts as evil from the night they turn. |
| **Fortune Teller** | Each night, choose 2 players: you learn if either is a Demon. There is a good player that registers as a Demon to you. | Both picks can be redirected by the Wrecker. Place the red herring with intent (see Dials). |
| **Slayer** | Once per game, during the day, publicly choose a player: if they are the Demon, they die. | Day action — never affected by the Wrecker. Recluse may die to it. |
| **Virgin** | The 1st time you are nominated, if the nominator is a Townsfolk, they are executed immediately. | A Devil's Advocate-protected nominator is executed and survives, which makes the Virgin look broken. |
| **Ravenkeeper** | If you die at night, you are woken to choose a player: you learn their character. | Triggers on any night death, including the Albatross's curse. Choice can be wrecked. |
| ★ **Cartographer** | You start knowing how many seats away the nearest evil player is. | A neighbour is 1. Take the shorter direction. Skip the Stowaway's seat. Recluse may count. |
| ★ **Lighthouse Keeper** | Each night\*, choose a player (not yourself): they are safe from the Demon tonight. The next day, all players learn who you chose. | Announce at dawn: "Last night, the lantern fell on [X]." Name where the light *actually* fell (after any Wrecker redirect). |
| ★ **Harbourmaster** | Once per game, during the day, publicly close the harbour: nobody dies tonight. | Blocks every night death from any source, and the Siren's conversion. If drunk/poisoned when declaring, the closure silently fails and the ability is spent. |
| ★ **Pearl Diver** | Each night\*, choose a player who died at night: you learn their character. | Night deaths only, never executions. A self-killed Imp reads as "Imp". The dead Drunk reads as "Drunk". |
| ★ **Navigator** | Once per game, at night\*, choose 2 players: you learn how many alive evil players sit between them, clockwise from the 1st. | Exclusive of the two chosen. Skip the Stowaway's seat. Wake them each night\* until used; they may decline. |
| ★ **Ferryman** | If you die, choose another dead player: if they are good, they are resurrected tonight. | Choose someone other than the Ferryman; if nobody else is dead, there is no crossing. Choice made at the moment of death. If the Ferryman is drunk/poisoned when they die, nothing happens. A converted (evil) Outsider cannot be brought back. |

### Outsiders

| Character | Ability | Storyteller notes |
|---|---|---|
| **Drunk** | You do not know you are the Drunk. You think you are a Townsfolk character, but you are not. | The one purely false reading in the beginner bag. Choose the sham with intent. |
| **Recluse** | You might register as evil & as a Minion or Demon, even if dead. | Also an Outsider — the Siren can convert them, and then they really are evil. |
| ★ **Stowaway** | Abilities that count or measure seats treat your seat as empty. | Affects Chef, Empath, Cartographer, Navigator. Does not affect votes, majority, kills, or any "choose a player" ability. The Stowaway knows what they are. |
| ★ **Albatross** | If you are executed, the player who nominated you dies tonight. | Not blocked by the Keeper (it isn't the Demon). Blocked by a closed harbour. If the nominator is the Demon, the Demon dies at night and good wins. |

### Minions

| Character | Ability | Storyteller notes |
|---|---|---|
| **Poisoner** | Each night, choose a player: they are poisoned tonight and tomorrow day. | Held out of the beginner bag on purpose — see the misinformation ladder. |
| ★ **Wrecker** | Each night, choose 2 players: tonight, if a good player's ability would choose either of them, it chooses the other instead. | Silent swap. Only applies when the substitute is a legal target for that ability. Evil abilities are never redirected. |
| ★ **Smuggler** | Each night, choose a player: you learn what they learned tonight. | Wake last. Show exactly what the target was shown (fingers, tokens, yes/no) or shake your head. Does not reveal the character directly. |
| **Devil's Advocate** | Each night, choose a living player (different to last night): if executed tomorrow, they don't die. | The script's only execution protection. An executed-but-alive Albatross still curses its nominator; an executed-but-alive Ferryman makes no crossing. |

### Demons

| Character | Ability | Storyteller notes |
|---|---|---|
| **Imp** | Each night\*, choose a player: they die. If you kill yourself this way, a Minion becomes the Imp. | Starpassing under a living Pearl Diver leaves an "Imp" body to be read. That's a real cost, and intended. |
| **Pukka** | Each night, choose a player: they are poisoned. The previously poisoned player dies then becomes healthy. | The "displaced in time" Demon: it acts on night one, so a starting-info role can be poisoned before it ever speaks. |
| ★ **Siren** | Each night\*, choose a player: they die. The 1st time you choose an Outsider, they do not die: they become evil instead, and learn who you are. | Converted player keeps their character and its ability, but is evil. No body that night. Keeper protection or a closed harbour blocks it, and the "1st time" is *not* used up. |
| ★ **Kraken** | Each night\*, choose a player: they die. If a Minion died today, you may choose a 2nd player: they die too. | "Today" = the day just ended (execution, Virgin). Minion deaths at night don't count. Each target checks protection separately. |

---

## 3. Jinxes & rulings

Formal jinxes (also embedded in the JSON):

- **Harbourmaster / Siren** — If the harbour is closed, the Siren's ability does nothing tonight; no one is converted, and the Siren's first conversion remains available.
- **Harbourmaster / Pukka** — If the harbour is closed, the Pukka's previously poisoned player does not die and becomes healthy. The Pukka still poisons a new player.
- **Lighthouse Keeper / Pukka** — If the Keeper protects the Pukka's previously poisoned player, that player does not die and becomes healthy.
- **Lighthouse Keeper / Siren** — A player made safe by the Keeper cannot be converted; the Siren's first conversion is not spent.
- **Wrecker / Pearl Diver** (and any restricted-target ability) — A Wrecker redirect only happens if the other marked player is a legal choice for that ability. Otherwise the choice stands.
- **Lighthouse Keeper / Wrecker** — The dawn announcement names the player the light actually fell on.
- **Siren / Drunk** — A converted Drunk is told they were the Drunk. They remain the Drunk (no ability), now evil.
- **Ferryman / Siren** — A converted Outsider is evil and cannot be resurrected by the Ferryman.
- **Ferryman / Wrecker (v1.2)** — If the Wrecker's ability is working and both the dead Ferryman and the chosen other dead player are Wrecked, the Wrecker redirects a good Ferryman's night choice to the Ferryman themself. The original choice can be good or evil, but must be another dead player. This never permits a direct self-choice or a choice when nobody else is dead, and day choices are not redirected. The Ferryman is resurrected only if good and not drunk or poisoned when they died.
- **Devil's Advocate / Albatross** — The Albatross triggers on being executed, not on dying. An Albatross protected by the Devil's Advocate who is executed survives, and their nominator still dies tonight.
- **Devil's Advocate / Ferryman** — A Ferryman who is executed but survives has not died; no crossing.
- **Devil's Advocate / Kraken** — A Minion who is executed but survives has not died; no thrash.

General rulings:

- **Ferryman targets (v1.2).** The Ferryman must directly choose another dead player, good or evil. If nobody else is dead, there is no crossing. The Ferryman / Wrecker jinx is the only way this ability can bring the Ferryman themself back. This ability has no once-per-game limit.

- **Dusk deaths.** The Albatross's cursed nominator dies at the start of the night, before the Poisoner acts. The Ravenkeeper and Ferryman trigger normally from it.
- **"Safe from the Demon"** means the Demon's ability does nothing to that player tonight: no Imp/Kraken kill, no Pukka death (they become healthy), no Siren conversion.
- **Drunk & poisoned homebrews.** Cartographer/Navigator: any number. Keeper: no protection; the announcement may name any player. Harbourmaster: closure fails. Diver: any character. Ferryman: no resurrection. Albatross: no curse. Wrecker: no redirect. Smuggler: any information. Siren: neither kill nor conversion. Kraken: no kills.
- **Converted Outsiders** register as evil to Empath, Chef, Cartographer, Navigator and the Fortune Teller's alignment side ("no" — they aren't a Demon). They do not wake with Minions and are not Minions (executing one does not trigger the Kraken). They keep their own ability: a converted Albatross still curses its nominator, a converted Stowaway is still invisible to seat maths, a converted Recluse still misregisters.

---

## 4. Night order

### First night
1. Minion info (7+ players)
2. Demon info & 3 bluffs (7+ players)
3. **Poisoner** — choose a player
4. **Wrecker** — choose 2 players; mark both WRECKED
5. **Devil's Advocate** — choose a living player; mark SURVIVES EXECUTION
6. **Pukka** — choose a player (poisoned)
7. **Investigator** — show Minion token + 2 players
8. **Chef** — show a number
9. **Empath** — show a number
10. **Cartographer** — show a number (seats to nearest evil)
11. **Fortune Teller** — choose 2 (apply Wrecker); nod or shake
12. **Smuggler** — choose a player; show what they learned tonight

### Other nights
1. Dusk deaths — Albatross's cursed nominator dies (unless the harbour is closed)
2. **Poisoner**
3. **Wrecker** — choose 2; mark WRECKED
4. **Devil's Advocate** — choose a living player (not last night's); mark SURVIVES EXECUTION
5. **Lighthouse Keeper** — choose a player (apply Wrecker); mark SAFE
6. **Demon** — Imp / Pukka / Siren / Kraken acts. Siren: if an Outsider converts, wake them, point at the Siren, show their true character, thumbs-down; show the Siren a thumbs-up. Kraken: if THRASH is out, offer a 2nd choice.
7. **Ferryman** — if they died tonight (or were executed today): directly choose another dead player; apply Wrecker, including the self-return jinx, then resurrect the final target if good. If nobody else is dead, no crossing
8. **Ravenkeeper** — if they died tonight: choose a player (apply Wrecker); show character
9. **Pearl Diver** — choose a night-dead player (apply Wrecker); show character
10. **Empath**
11. **Fortune Teller** (apply Wrecker)
12. **Navigator** — if unused: may choose 2 (apply Wrecker); show a number
13. **Smuggler** — choose a player; show what they learned tonight

Day: Slayer, Virgin, Harbourmaster, Albatross (on execution), Devil's Advocate (on execution), Ferryman (choose another dead player on day death; never themself, and no crossing if nobody else is dead).

---

## 5. The bag

### 12 players (7 Townsfolk · 2 Outsiders · 2 Minions · 1 Demon) — the first-game bag

| In the bag | |
|---|---|
| Townsfolk | Chef · Empath · Cartographer · Fortune Teller · Lighthouse Keeper · Pearl Diver · Harbourmaster |
| Outsiders | Drunk (sham: **Investigator**) · Stowaway |
| Minions | Wrecker · Smuggler |
| Demon | Siren |
| Demon's bluffs | Slayer · Ravenkeeper · Navigator |
| Deliberately out | Investigator (the Drunk's sham — don't hand it out as a bluff), Virgin, Ferryman, Recluse, Albatross, Poisoner, Devil's Advocate, Imp, Pukka, Kraken |

**Why these tokens**

1. **The positional triangle plus the Stowaway.** Chef, Empath and Cartographer are three true readings that only agree once the table realises one seat doesn't count. It teaches "displaced, not false" on night one, and it hands evil a real bluff: any evil player can claim Stowaway to explain why an Empath number "doesn't fit". Without the Stowaway the triangle is a calculation; with it, it's a puzzle.
2. **Fortune Teller.** The only nightly Demon-detector in the bag, so it's the Wrecker's favourite victim and the Keeper's favourite ward. The FT can't see when their pair has been swapped, which is exactly why the Empath is there to cross-check.
3. **Lighthouse Keeper and Harbourmaster, no Monk.** Together they give a 12-player table roughly four days — long enough to solve the triangle, short enough that the Siren's clock still bites. Both are public: the Keeper's dawn light is the town's daily shared datum (and the only way to catch a Wrecker red-handed), and the closure is one guaranteed breathing day that also lie-detects a fake Harbourmaster. The Keeper *is* the Monk here, just louder.
4. **Pearl Diver.** In a bag with the Siren, bodies are evidence. The Diver confirms the dead's claims, unmasks the dead Drunk, and — crucially — has nothing to read on a conversion night. A quiet night with no lantern save and no closure is either a Keeper hit or a Siren song, and the Diver is how the town tells the difference over time.
5. **Drunk as Investigator.** The one purely false reading. A night-one "1 of 2 is the Wrecker" is something you can aim: at the real Wrecker plus an innocent (kind), at two innocents (cruel), or as "1 of 2 is a Smuggler" pointing at a Townsfolk and the Siren herself (chaos). It gives evil something to push on day one, which a silent minion pair otherwise lacks.
6. **Wrecker.** The signature Minion. Two marks a night, bending the FT, the Keeper and the Diver on this bag. The Keeper's dawn announcement is the town's *only* window onto it. That asymmetry — one public signal against a silent hand on the lantern — is the script in miniature.
7. **Smuggler.** The second Minion is intelligence, not corruption. Peeking the Empath's real number and the Cartographer's real distance makes the Demon's bluffs airtight, which is worth more here than a second poison. It also keeps evil quiet: two silent Minions and a silent Demon mean the day is driven by the town's public tools.
8. **Siren.** A Stowaway who has to claim early to make the maths work, and a Drunk who doesn't know what they are, is real bait. Her ceiling is high (+1 evil, once), so the rest of the bag leans good: no poison, no Pukka, only displacement. **If your table is new to the script, swap Siren → Imp and change nothing else.**

**Setup checklist for this bag**
- Write the physical seating, then a second list with the Stowaway removed. Use the second list for Chef, Empath, Cartographer, Navigator.
- Place the FT red herring on a Townsfolk seated far from the Siren (default) or on the Stowaway (chaos).
- Decide the Drunk's night-one pointer before you open the bag.
- Give the Siren her bluffs face-down with a note that Navigator is "used it already / haven't yet" flexible.
- Have the dawn line ready: "Last night, the lantern fell on [X]."

### 9 players (5 / 2 / 1 / 1) — the loud small table

| In the bag | |
|---|---|
| Townsfolk | Empath · Fortune Teller · Cartographer · Pearl Diver · Harbourmaster |
| Outsiders | Drunk (sham: **Slayer**) · Albatross |
| Minion | Devil's Advocate |
| Demon | Kraken |
| Bluffs | Chef · Ravenkeeper · Navigator |

Nine-player games are decided by two or three executions, so the small table's game is the day. The Albatross punishes a careless nomination, the Devil's Advocate can turn the town's best execution into a survivor (and a protected Albatross into a two-for-one), and the Kraken makes "just execute the Devil's Advocate" cost a second body that night — the town has to sequence, not stampede. The Drunk-Slayer is a comedy timer that also makes the real day tools look suspicious. The Diver keeps score.

### 15 players (9 / 2 / 3 / 1) — the full fog

| In the bag | |
|---|---|
| Townsfolk | Chef · Investigator · Empath · Fortune Teller · Cartographer · Lighthouse Keeper · Pearl Diver · Navigator · Ferryman |
| Outsiders | Recluse · Stowaway |
| Minions | Poisoner · Wrecker · Devil's Advocate |
| Demon | Pukka |
| Bluffs | Slayer · Virgin · Harbourmaster |

A big table can absorb the whole misinformation ladder at once: the Poisoner falsifies, the Wrecker displaces, the Pukka's night-one poison hits a starting reading before anyone knows there's a Pukka. Good's stamina comes from the Keeper and the Ferryman; the Investigator, Navigator and Recluse make the Minion hunt a proper puzzle; the Devil's Advocate means the day the town finally agrees on someone may not be the day it thinks. Pukka's best line is poisoning the Ferryman before killing them — no coin, no crossing.

### Dials you control

- **The Drunk's sham.** Investigator = a false pointer evil can lean on. Slayer = a dud shot at the worst moment. Ravenkeeper = invisible until it isn't.
- **The red herring.** On a quiet Townsfolk it protects the Demon; on a claimed Outsider it starts a fight.
- **Recluse registration** for Chef, Cartographer, Navigator, Investigator, Diver, Slayer.
- **Poisoned Keeper's announcement.** Usually name their real choice (hides the poison). Occasionally name the night's victim (frames the Keeper as a liar).
- **The Drunk's night-one number/pointer**, and whether it happens to be right.
- **Imp starpass recipient** when there are two Minions.
- **Pukka's night-one target** is the Pukka's, not yours — but the Pukka's bluffs are yours. Give them a starting-info role so they can claim poisoned-sounding info credibly.

---

## 6. Running the homebrews

**Cartographer.** Count seats outward in both directions, take the smaller. "1" is an Empath "1" frozen in time. "2" also says both neighbours are good. "3+" is rare and strong; you can't change the seating, so don't try — that's what the Drunk, the Stowaway and the Recluse are for. Show fingers.

**Lighthouse Keeper.** Wake after the Wrecker, before the Demon, so the redirect is already marked. Mark SAFE on where the light actually lands. At dawn, announce that player by name, every day the Keeper is alive — the moment the announcements stop, the town knows the Keeper is dead, which is exactly why evil can't hold a Keeper bluff for long. The Keeper can waste the light on a dead player; let them.

**Harbourmaster.** "I'm closing the harbour" at any point during the day. Mark CLOSED, and at night let every death-causing ability act as normal but resolve to nothing: no Demon kill, no Kraken thrash, no Pukka death (healthy), no Albatross curse, no Siren conversion. A Ferryman crossing still happens; the harbour keeps ships in, it doesn't keep them out. If the declarer was drunk or poisoned, say nothing and let the night run.

**Pearl Diver.** Legal targets are players who died at night — any night. If none, shake your head. Show the true character token: a dead Drunk shows Drunk, a starpassed Imp shows Imp, a dead Recluse may show a Minion or Demon. A Wrecker redirect only applies if the other marked player is also a night-dead player.

**Navigator.** Once per game; wake them each night\* until they use it. From the first chosen player, walk clockwise to the second, count alive evil players strictly between, skipping the Stowaway's seat. If the Wrecker has swapped the two endpoints, the walk goes the other way round the table — that's a legitimate displacement, and a fun one.

**Ferryman.** Directly choose another dead player, good or evil; never directly choose the Ferryman themself. If nobody else is dead, there is no crossing. Apply ordinary legal Wrecker swaps between other dead players. Exception: if the Wrecker's ability is working and both the chosen player and the dead Ferryman are Wrecked, a good Ferryman's night choice redirects to the Ferryman, allowing self-resurrection if they were not drunk or poisoned when they died. Day choices are never redirected. The ability is not once per game. The choice is made when they die. By day: ask them quietly, or let them declare it; either is fine. By night: wake them at the Ferryman step and let them point. Resurrect at that step (after the Demon has acted, so the returning player can't be killed tonight). Used once-per-game abilities stay used; nightly abilities resume. Announce the return at dawn like any other state change. Poisoned or drunk at the moment of death: nothing, and no explanation.

**Stowaway.** Keep the "Stowaway-removed" seating list in the grimoire. Affects Chef, Empath, Cartographer, Navigator — nothing else. The Stowaway can still be chosen by the Navigator as an endpoint; measure from their physical seat. Dying changes nothing (dead players are already skipped by the Empath; the other three only measure on night one or by choice). Tell the Stowaway plainly that they should probably claim early and that the Siren, if in play, would like that very much.

**Albatross.** When they are executed, mark CURSED on the nominator. That player dies at the start of the night. Don't announce a cause at dawn — it's just a death. If the Demon nominated, the Demon dies at night and the game ends in good's favour. If the Albatross was protected by the Devil's Advocate, they survive the execution and the nominator is still cursed: the trigger is the execution, not the death.

**Wrecker.** Wake after the Poisoner every night. Two WRECKED tokens. For the rest of the night, whenever a *good* player points at a WRECKED player, resolve the ability on the other WRECKED player, silently — Fortune Teller (each pick), Keeper, Ravenkeeper, Diver, Navigator, Ferryman. If the substitute isn't a legal target, the choice stands, except for the explicit Ferryman jinx: a good Ferryman's legal night choice of the other Wrecked dead player redirects back to the Wrecked dead Ferryman. Never redirect evil abilities. Never tell the good player. The only trace it leaves is the dawn announcement naming a player the Keeper didn't send the light to.

*Example.* Wrecker marks Anna and Ben. The Keeper protects Anna → Ben is SAFE. The Fortune Teller picks Anna and Cal → you resolve Ben and Cal. The Siren kills Anna. Dawn: "Anna died. Last night the lantern fell on Ben." The Keeper knows something moved the light; whether they say so out loud is the day's first decision.

**Smuggler.** Last to wake. They point; you show exactly what that player was shown tonight — a number of fingers, a character token, a nod/shake — or shake your head if they learned nothing (no ability, ability unused, protection-type ability). Don't show reminder tokens or the character itself. Peeking the Drunk shows the Drunk's false information, unflagged.

**Siren.** Acts with the other Demons. If the chosen player is an Outsider, the Siren hasn't converted yet, the player isn't SAFE and the harbour isn't closed: mark SUNG on the Siren, flip the Outsider's alignment, wake the Outsider, point at the Siren, show their true character token (the Drunk finds out), thumbs-down. Then show the Siren a thumbs-up. Nobody dies. If it was blocked, do nothing and tell the Siren nothing; the first conversion remains available. The converted player doesn't wake with Minions and gets no new ability — they are their old character, now on the other team, and should spend tomorrow finding out who their friends are.

**Kraken.** When a Minion dies by day, put THRASH on the Kraken so you remember at night. At the Kraken step: one kill, then if THRASH is out, offer a second (different) target; each checks SAFE separately. Clear THRASH. A Minion "confession" to buy a double kill is a legitimate evil play — surprising, expensive, and occasionally correct.

---

## 7. Playbooks

**Good**
- Draw the seat map first, with a hole where the Stowaway sits. Nothing else makes sense until you do.
- Outsiders should claim early — the maths needs it — knowing that the Siren is listening. The counter is the Keeper: an Outsider who's claimed is an Outsider you can ward.
- Ward the Fortune Teller or the Empath, never the loudest player. The Demon already knows who's loud.
- Save the closure for the day you have a real plan, or the day you're at four alive. A closure at four turns the next day into a free execution.
- Diver: check the dead who claimed something you doubted. Then the dead who died the night nobody expected.
- If the lantern lands somewhere the Keeper didn't send it, stop everything and hunt the Wrecker. It's the only fingerprint they leave.
- No body, no light, no closure: someone was sung to. Ask who among the Outsiders looks a little too calm.

**Evil**
- Wrecker: mark the Fortune Teller's likeliest picks, or the player you're about to kill (so the Keeper's light slides off them).
- Smuggler: night one is Cartographer or Empath, never the Chef — Chef numbers are easy to fake, distances aren't.
- Siren: convert an Outsider *before* the town has finished trusting them, not after. A converted Stowaway is an evil player the seat maths will never find.
- Kraken: a Minion who "comes clean" on day three and eats an execution buys two bodies that night. Use once, use late.
- Devil's Advocate: protect the claimed Albatross on the day the town has talked itself into executing them, and the Ferryman on the day they announce who they'd bring back.
- Bluff Stowaway to erase an Empath, Albatross to survive a day, Navigator to stall forever, Ferryman to make evil weigh which other dead good player could return.
- Pukka: night one, the loudest starting-info role you can guess. A poisoned Cartographer who confidently announces "2" on day one is worth three Poisoner nights.

---

## 8. Design notes

**Why 13/4/4/4 and thirteen homebrews.** The standard shape means every player already knows the bag maths. Twelve homebrews is the most I could fit while keeping every ability to one or two sentences and every homebrew mechanic to one job. The official seven Townsfolk are there to be the "known quantities" the homebrews are measured against.

**Info economy, Townsfolk side.** Three "who" readings that are positional (Chef, Empath, Cartographer), one that isn't (Investigator), one Demon-detector (FT), one rangefinder (Navigator), two "what" readings from the dead (Diver, Ravenkeeper), two protections that are public (Keeper, Harbourmaster), two day weapons (Slayer, Virgin), one recovery (Ferryman). No "not in play" info: the Demon's bluff kit is generous enough without it.

**Info economy, evil side.** One falsifier (Poisoner), one displacer (Wrecker), one intelligence tool (Smuggler), one day trick (Devil's Advocate). Demons split the same way: succession (Imp), time (Pukka), recruitment (Siren), tempo (Kraken).

**What I cut, and why**
- *Almanac* ("you start knowing a night on which the Demon's attack fails") — a lovely prediction, but it overlapped the Harbourmaster and took the decision out of the player's hands.
- *Barnacle* (an Outsider whose life is tied to another player's) — charming, and too much protection on a script that already has the Keeper and the Harbourmaster.
- *Monk, Undertaker, Seamstress, Professor* — each is covered by a homebrew that does the same job with more texture (Keeper, Diver limited to night deaths, Navigator, Ferryman).
- *Lleech* — I wanted it for the theme, but the Pukka's night-one poison serves "displaced in time" better. It is the swap I'd make first if you want a host-based endgame.
- *A "Haar" Demon that registers as good* — it would have made the Fortune Teller decorative.
- *A redirect Minion that also redirected evil abilities* — evil tripping over its own team is a bad kind of chaos.
- *Press-Gang* (v1.0: "nominate tomorrow or die") — too easy to satisfy, and every buff turned it into the Harpy. Replaced by the Devil's Advocate, which adds an axis the script lacked: execution protection.
- *Spy* — considered for the same slot. It would make the Smuggler the strictly worse token, and seeing the Grimoire answers every "true about whom?" question for one team on night one.

**Balance risks I'm watching**
- The Siren's ceiling. She is the strongest Demon here. The beginner bag compensates by carrying no poison. If your group finds she's winning too often, give her a bag with the Poisoner and no Smuggler, so evil's information and evil's numbers don't both peak.
- A Cartographer "3". It confirms four good players and halves the Demon's hiding room on night one. The Drunk and the Stowaway exist partly to keep the table from treating any single number as gospel.
- The Ferryman trades their own life for another dead good player. With no other dead player available, killing the Ferryman produces no crossing; with several dead players, evil must weigh which good ability or vote might return. The Pukka and the Poisoner can suppress the crossing. The Ferryman cannot directly choose themself; the Wrecker jinx can instead turn a legal night choice of another dead player into an accidental self-resurrection.
- Keeper + Harbourmaster stalls at four alive. If your table plays for the stall every game, move the Harbourmaster out of bags that already have the Keeper.
- Stowaway adjudication load. Keep the second seating list in the grimoire and it's fine; try to do it in your head and it isn't.

**Flavour text**
- Cartographer — *Every coast is the same coast. Only the rocks move.*
- Lighthouse Keeper — *Some nights the light is a warning. Some nights it is a promise. The sea has never learned the difference.*
- Harbourmaster — *Chain across the mouth of the bay. Nothing in, nothing out, nothing lost.*
- Pearl Diver — *The sea keeps what it takes. She goes down and takes it back.*
- Navigator — *Two stars, and the stretch of dark between them.*
- Ferryman — *One coin, one crossing. No refunds. Exchanges considered.*
- Stowaway — *There is no such person on the manifest.*
- Albatross — *It followed the ship for nine days. On the tenth, someone got clever.*
- Wrecker — *A lantern on the wrong headland has drowned more sailors than any storm.*
- Smuggler — *Everything that comes into this port comes through me first.*
- Siren — *You will not remember drowning. You will only remember that she asked.*
- Kraken — *Cut off one arm and eight more come looking for whoever held the knife.*

---

## 9. Reminder tokens

| Character | Tokens |
|---|---|
| Lighthouse Keeper | SAFE |
| Harbourmaster | CLOSED |
| Navigator | NO ABILITY |
| Ferryman | CROSSING |
| Albatross | CURSED |
| Wrecker | WRECKED ×2 |
| Siren | DEAD, SUNG |
| Kraken | DEAD ×2, THRASH |
| Official characters | as printed |

## 10. Files

- `salt-and-lantern.md` — this document.
- `salt-and-lantern.json` — the script for the official script tool or any town-square app that accepts custom characters. Official characters are referenced by ID so the tool pulls their canonical text and art; the twelve homebrews are defined in full, jinxes included. Night-order numbers on the custom characters slot them where Section 4 puts them; if your tool orders differently, adjust the `firstNight` / `otherNight` values to taste.
