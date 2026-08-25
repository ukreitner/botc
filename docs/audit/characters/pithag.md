# Pit-Hag (pithag) — Sects & Violets Minion

## Official rules (sources)

Sources: https://wiki.bloodontheclocktower.com/Pit-Hag (Character Text, How to
Run, Examples, Strategy, Jinxes),
https://wiki.bloodontheclocktower.com/Abilities (the canonical "becomes a new
character" rules), https://wiki.bloodontheclocktower.com/Evil_Twin (Pit-Hag
final-night clause).

Current ability text (verbatim):

> "Each night*, choose a player & a character they become (if not in play). If a
> Demon is made, deaths tonight are arbitrary."

How to Run (wiki):

- "Each night **except the first**, wake the Pit-Hag. They point at any player
  and any character icon on their character sheet."
- "**If that character is already in play, nothing happens.**"
- "If not in play, wake the chosen player, show them the **YOU ARE** token and
  their new character token, then sleep them. **Replace their old token with the
  new one.**"
- "**When a Demon is created, you may choose any players to kill or protect
  throughout the night to balance the game. Additional deaths count as Pit-Hag
  attacks.**"
- "If changing a Demon into another Demon, consider no death (signalling a Demon
  change) or killing the former Demon plus another player. If creating a good
  Demon, decide whether to kill one (final night) or allow both Demons to
  operate."

Consequences of becoming a new character (Abilities page, verbatim):

- "If a player becomes a new character, they gain the new character's ability
  immediately."
- "They lose their old ability immediately and **any of its persistent effects
  end**."
- "If the new ability is a 'once per game' ability and has already been used,
  **they may use it again**."
- "**If the new ability normally only functions on the first night, it functions
  tonight.**"
- "**If a player becomes a Minion or Demon character, they do not learn who the
  other evil players are.**"

Alignment: **unchanged.** Pit-Hag wiki, good-team strategy: "alignment persists
despite character changes." Evil Twin wiki: "If a good player becomes an Evil
Twin: **They remain good-aligned**." Wiki example: "Flowergirl → Evil Twin (good
Evil Twin created; Evil Twin and evil player learn each other)".

Examples (wiki): Clockmaker → Mutant (works); Savant → Sage (**nothing happens**,
the Sage is already in play); Flowergirl → Evil Twin; Oracle → good No Dashii on
the final night (the storyteller kills only the evil Demon).

**Jinxes** (verbatim from the wiki's Jinxes section):

| Partner | Text |
| --- | --- |
| Cult Leader | "If the Pit-Hag turns an evil player into the Cult Leader, they can't turn good due to their own ability." |
| Damsel | "If a Pit-Hag creates a Damsel, the Storyteller chooses which player it is." |
| Goon | "If the Pit-Hag turns an evil player into the Goon, they can't turn good due to their own ability." |
| Heretic | "Only 1 jinxed character can be in play." |
| Leviathan | "The Leviathan cannot enter play after day 5." |
| Ogre | "If the Pit-Hag turns an evil player into the Ogre, they can't turn good due to their own ability." |
| Politician | "If the Pit-Hag turns an evil player into the Politician, they can't turn good due to their own ability." |
| Summoner | "If the Summoner creates a second living Demon, deaths tonight are arbitrary." |
| Village Idiot | "If there is a spare token, the Pit-Hag can create an extra Village Idiot. If so, the drunk Village Idiot might change." |

Night order: **other nights only**, official slot between the Cerenovus and the
Fearmonger — i.e. **before every Demon**, before the Scarlet Woman, the
Summoner, the Lunatic and the Exorcist. A newly created Demon therefore acts
**that same night**; a newly created early-order character (Poisoner,
Innkeeper, Courtier…) has already been passed.

**Not stated on the wiki, flagged rather than asserted:** the page says nothing
about a drunk/poisoned Pit-Hag, self-targeting, or targeting a dead player. The
general rules give: impaired → nothing happens; self and dead targets are legal
("any player").

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:1040-1050` — ability text
  matches the wiki; `otherNightReminder` is a faithful transcription;
  `reminders: []` (correct — there is no official Pit-Hag token).
- `engine/src/main/resources/botc/data/night_guide.json:697-709` — a good `other`
  entry: covers "if in play, nothing happens", the arbitrary-deaths clause,
  "alignment does not change", and "If the Pit-Hag is drunk or poisoned, no
  change occurs". One `pick` show card ("New character" / "You are…").
- `engine/src/main/resources/botc/data/night_and_jinxes.json:398` — other-night
  index 25, correct. **Works.**
- `engine/src/main/resources/botc/data/night_and_jinxes.json:49,74,84` — only
  **three** of the nine official jinxes are present (Damsel, Heretic, Cult
  Leader), and the Heretic text reads "A Pit-Hag can not create a Heretic."
  rather than the wiki's "Only 1 jinxed character can be in play."

Engine:
- `engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:46-53` —
  `assignCharacter` is the only mechanism: it sets `characterId`, clears
  `shownCharacterId`, sets `isTraveller`. It does **not** touch
  `alignmentFlipped`, does not remove the outgoing character's reminders, does
  not reset once-per-game markers, and emits no follow-up.
- No `pithag` reference anywhere in `engine/src/main`.

UI:
- `app/.../NightScreen.kt:470-524` — no `pithag` branch in `QuickResolutions`;
  the step is guide prose plus one generic "New character" show chip.
- `app/.../SeatSheet.kt:88-96, 388-453` — the only character-change path:
  Grimoire tab → tap seat → "Change character" → `CharacterPicker`. The picker
  annotates in-play characters with a grey "in play" label
  (`SeatSheet.kt:469-476`) but **still lets you pick them**.
- `app/.../NightScreen.kt:364-454` — the "New character" show card requires
  finding the character in a search grid a second time.

Storyteller experience: at the Pit-Hag step the app tells you what to do and
then leaves. You switch tabs, open a seat, scroll a picker with no not-in-play
filter, reassign, come back, re-find the character in a second picker to show
the card — and then you are entirely on your own for the alignment, the stale
reminder tokens, the new character's first-night ability, the new Demon's
arbitrary deaths, the new Demon's (absent) evil-team info, and the nine jinxes.

## Defects and gaps

1. **P0** · A character change silently changes the player's alignment ·
   `assignCharacter` (`GameActions.kt:46-53`) leaves `alignmentFlipped`
   untouched, and `Player.isEvil` (`GameState.kt:49-52`) is
   `characterTeam.isEvil != alignmentFlipped`. So turning a good Townsfolk into
   the Evil Twin makes the app treat them as **evil** — contradicting "alignment
   persists despite character changes" and breaking the Evil Twin, Empath,
   Fortune Teller, Undertaker, Seamstress, Cult Leader and every win check
   downstream. · Repro: assign `eviltwin` to a Townsfolk seat and read the seat
   sheet's alignment line (`SeatSheet.kt:186-189`).

2. **P0** · "(if not in play)" is not enforced · The picker lists every script
   character with a cosmetic "in play" tag (`SeatSheet.kt:469-476`); nothing
   blocks the illegal pick and nothing implements "nothing happens". · Repro:
   turn the Savant into the Sage while a Sage is in play.

3. **P0** · The new character's abilities are never run · Per the Abilities
   page the new ability works immediately, first-night-only abilities "function
   tonight", and spent once-per-game abilities reset. The app does none of it:
   no prompt to give a created Washerwoman/Investigator/Chef/Steward their info
   that night, no clearing of an inherited `No ability` / `Once` token, no
   re-sequencing of the night sheet. · `GameActions.kt:46-53`,
   `NightScreen.kt:84-90` · Repro: turn a player into the Washerwoman on night 3.

4. **P0** · "If a Demon is made, deaths tonight are arbitrary" is unmodelled ·
   The single most consequential clause on the card. There is no signal, no
   override of the normal Demon kill, no "kill or protect anyone" tool, no note
   that additional deaths count as Pit-Hag attacks. The Demon's row will simply
   show the generic `DemonKillPanel` (`NightScreen.kt:518-523`) as if the night
   were normal. · Repro: create a Demon at the Pit-Hag step, then open the
   Demon's row.

5. **P0** · The old character's persistent effects are not ended · "They lose
   their old ability immediately and any of its persistent effects end." A
   Pit-Hagged Witch keeps their `Cursed` token on a victim, a Pit-Hagged
   Cerenovus keeps their `Mad` token, a Pit-Hagged Fortune Teller keeps the
   `Red herring`, a Pit-Hagged Devil's Advocate keeps `Survives execution`. ·
   `GameActions.kt:46-53` · Repro: curse someone as the Witch, then Pit-Hag the
   Witch into a Townsfolk; the curse is still live in
   `StatusEffects.nominationWarnings`.

6. **P1** · A created Minion/Demon must not learn the evil team, and nothing
   says so · "If a player becomes a Minion or Demon character, they do not learn
   who the other evil players are." The night sheet's `MINION_INFO` /
   `DEMON_INFO` steps are first-night only (`NightOrder.kt:60-119`), so the app
   will not wrongly *run* them — but a storyteller looking at a brand-new Demon
   row with no guidance may improvise bluffs and a minion pointing. Needs an
   explicit "do NOT give evil info / do NOT give bluffs" line.

7. **P1** · No change tool at the night step · Three tab switches and two
   pickers for what should be player-chip → character-chip → confirm. ·
   `NightScreen.kt:470-524`.

8. **P1** · Six of nine jinxes are missing from the data · Only Damsel, Heretic
   and Cult Leader exist (`night_and_jinxes.json:49,74,84`); Goon, Ogre,
   Politician, Leviathan, Summoner and Village Idiot are absent, so
   `GameData.activeJinxes` cannot surface them in the seat sheet
   (`SeatSheet.kt:222-235`) or the jinx dialog.

9. **P1** · Heretic jinx text has drifted · The data says "A Pit-Hag can not
   create a Heretic." The wiki's current text is "Only 1 jinxed character can be
   in play." These are materially different rules. Verify against the official
   jinx list before changing, but the drift is real.

10. **P1** · The Damsel jinx is unimplemented · "If a Pit-Hag creates a Damsel,
    the Storyteller chooses which player it is" — the app has no such
    interception; whatever seat the storyteller taps becomes the Damsel.

11. **P1** · The Leviathan day-5 rule is unimplemented · "The Leviathan cannot
    enter play after day 5." Nothing consults `state.cycle` when a character is
    assigned.

12. **P2** · An impaired Pit-Hag is not detected · The guide says "If the
    Pit-Hag is drunk or poisoned, no change occurs" (`night_guide.json:702`) but
    `StatusEffects.isImpaired` is never called for this step, unlike the Demon
    step which does warn (`NightScreen.kt:548-554`).

13. **P2** · A newly created character earlier in the night order is silently
    inserted above the storyteller's position · `NightScreen.kt:84-90`
    recomputes `steps` from `state.players`. Creating a Poisoner (other-night
    index 13) at the Pit-Hag step (index 25) adds a row the storyteller has
    already scrolled past, with no signal — and per the Abilities page that
    ability **does** function tonight.

14. **P2** · No log entry for a character change · The game log
    (`app/.../GameExtras.kt`) is death/nomination based; the most game-defining
    Pit-Hag action leaves no trace, and `DeathRecord.characterIdAtDeath`
    snapshots are the only surviving evidence.

15. **P3** · `shownCharacterId` is cleared unconditionally
    (`GameActions.kt:46-53`), which is right for most changes but wrong when the
    Pit-Hag creates a Drunk, Lunatic or Marionette — those need a new believed
    identity chosen at that moment, and the app should prompt.

## Proposed behaviour (spec)

### New engine action

Replace bare `assignCharacter` for in-game changes with a single
`becomeCharacter(state, playerId, newCharacterId, lookup, source: String)` that
implements the Abilities-page rules once, for every source (Pit-Hag, Barber,
Snake Charmer, Fang Gu, Imp star pass, Philosopher, Alchemist, Boffin, Amnesiac,
Deus ex Fiasco…):

1. **Preserve alignment.** Compute `wasEvil = player.isEvil(lookup)` before the
   change; afterwards set `alignmentFlipped = (newTeam.isEvil != wasEvil)`.
   (Callers that *do* change alignment — Snake Charmer, Fang Gu — opt out with a
   flag, as they already do at `GameActions.kt:64-96`.)
2. **End the outgoing character's persistent effects.** Remove every
   `PlacedReminder` in the whole grimoire whose `sourceId == oldCharacterId`.
3. **Reset once-per-game.** Remove the new character's `No ability` / `Once`
   tokens from this seat.
4. **Hidden identity.** If the new character is `drunk` / `lunatic` /
   `marionette`, raise the corresponding `HiddenIdentityDialog`
   (`GameShell.kt:709-749`) rather than clearing `shownCharacterId` silently.
5. **Emit follow-up obligations** (consumed by the night sheet and the
   day-start briefing):
   - `show-you-are` — one-tap `ShowCard.CharacterCard("YOU ARE", newId)` for
     that seat, pre-filled;
   - `run-first-night-ability` when the new character has a first-night-only
     ability (non-empty `firstNightReminder`, empty `otherNightReminder`) or any
     `InfoCalc.supports(newId)` ability that would otherwise have been passed
     tonight;
   - `no-evil-info` when the new character's team is MINION or DEMON:
     `<name> is now the <character>. Do NOT show them the other evil players and
     do NOT give bluffs.`
   - `arbitrary-deaths` when the new character's team is DEMON (see below).

### Night step

- when: other nights only. Wake condition: a seat holds `pithag` and is alive
  (or retains its ability via the Vigormortis).
- targets:
  - **player**: 1, any seat including dead seats, travellers and the Pit-Hag
    themself. Sort: living good players first.
  - **character**: 1, from the script, **filtered to not-in-play**, grouped by
    team. In-play characters shown greyed with the reason "already in play —
    nothing would happen", still tappable but routed to an explicit
    `Nothing happens` confirmation so the storyteller records the no-op.
  - An explicit **`Pit-Hag declines / no change`** button.
- immediate effects: `becomeCharacter(...)` as above, plus a log entry
  `Night N: <target> became the <character> (Pit-Hag)`.
- impairment: if `StatusEffects.isImpaired(state, lookup, pitHag)`, show the
  same red banner the Demon step uses (`NightScreen.kt:548-554`):
  `The Pit-Hag is drunk/poisoned — let them point, then change nothing.`
- deferred effects:
  - **When a Demon is created** (including Demon → different Demon), set a
    night-scoped flag `arbitraryDeathsTonight` and:
    - annotate **every** subsequent Demon row on tonight's sheet with
      `Deaths tonight are ARBITRARY — you choose who dies. The normal attack
      does not apply.`;
    - add an explicit **Arbitrary deaths** panel at the Pit-Hag step and at
      Dawn: a multi-select of seats to kill and seats to protect, applied on
      confirm with `DeathCause.OTHER_NIGHT_DEATH` (the wiki: "Additional deaths
      count as Pit-Hag attacks");
    - print the wiki's guidance verbatim as help text: Demon → Demon, consider
      no death or the old Demon plus one other; a **good** Demon created,
      decide whether to kill one (final night) or let both operate.
  - **Evil Twin created** → chain into the Evil Twin twin-picker (see
    `eviltwin.md`), with the wiki's final-night caution.
  - **Damsel created** → per the jinx, present a storyteller picker: "Which
    player becomes the Damsel?"
  - **Leviathan** → block (or hard-warn) when `state.cycle > 5`.
  - **Village Idiot** → allow a duplicate (`GameActions.DUPLICABLE` already
    contains `villageidiot`, `GameActions.kt:413`) and re-prompt which Village
    Idiot is drunk.
  - **Cult Leader / Goon / Ogre / Politician created from an evil player** →
    note "they can't turn good due to their own ability".
- expiry: none — a character change is permanent until changed again.
  `arbitraryDeathsTonight` clears at dawn.
- information: nothing computed for the Pit-Hag. The consequence is other
  characters' information (see `run-first-night-ability`).
- visibility: only the changed player is woken, and they see only the
  "YOU ARE" card and their new token. Nobody else learns anything — explicitly
  including the Demon and other Minions.
- day-time inputs: none.

### UI text for the step

- Title: `Pit-Hag — change a player's character`
- Body: `Wake <PitHag>. They point at a player, then a character. If that
  character is already in play, nothing happens.`
- Character list header: `Not in play (legal)` / `Already in play — nothing
  happens`
- After a Demon is created: `A Demon was created. Deaths tonight are ARBITRARY:
  you decide who dies and who survives, overriding the normal attack.`
- After any change: `Wake <name>: "You are the <character>." Do not give them
  evil info or bluffs.` / `<name> is now the <character> — run their first-night
  ability tonight.`

### Data changes

- `night_and_jinxes.json` — add the six missing jinxes with the wiki text
  quoted above (`pithag`×`goon`, `ogre`, `politician`, `leviathan`, `summoner`,
  `villageidiot`) and reconcile the Heretic text.
- `night_guide.json:697-709` — add: "The Pit-Hag may choose a dead player or
  themself. A created Minion or Demon does **not** learn the other evil players
  and gets no bluffs. A created character with a 'you start knowing' or 'once
  per game' ability gets it tonight, even if another player already used it."
- `characters.json:1040-1050` — no ability-text drift.

## Tests to add

1. **Given** a good Townsfolk, **when** `becomeCharacter(..., "eviltwin")` runs,
   **then** `player.isEvil(lookup)` is still `false`.
2. **Given** the evil Poisoner, **when** they become the Chef, **then**
   `player.isEvil(lookup)` is still `true`.
3. **Given** a Sage is in play, **when** the Pit-Hag targets the Savant with
   `sage`, **then** the engine returns the state unchanged and records a
   "nothing happens" log entry.
4. **Given** the Witch has placed `witch:Cursed` on a victim, **when** the Witch
   becomes the Chef, **then** no seat carries a `witch:*` reminder.
5. **Given** the Fortune Teller has a `fortuneteller:Red herring` on a seat,
   **when** the Fortune Teller becomes another character, **then** the herring
   is removed.
6. **Given** the Professor carries `professor:No ability`, **when** another
   player becomes the Professor, **then** the new Professor has no
   `No ability` token (once-per-game resets).
7. **Given** a player becomes the Washerwoman on night 3, **then** the engine
   emits a `run-first-night-ability` obligation and
   `InfoCalc.compute(..., "washerwoman", ...)` returns a result.
8. **Given** a player becomes the Imp on night 3, **then**
   `arbitraryDeathsTonight` is set, every Demon step on tonight's sheet carries
   the arbitrary-deaths annotation, and a `no-evil-info` obligation exists.
9. **Given** `arbitraryDeathsTonight` is set, **when** `advancePhase` reaches
   dawn, **then** the flag is cleared.
10. **Given** an impaired Pit-Hag, **then** the step shows the "change nothing"
    banner.
11. **Given** `state.cycle == 6`, **when** the Pit-Hag targets the Leviathan,
    **then** the engine refuses with the jinx text.
12. **Given** the Pit-Hag and Goon are on one script, **then**
    `GameData.activeJinxes` returns the Pit-Hag/Goon jinx (currently absent).
13. **Given** a player becomes the Drunk, **then** the engine requests a
    believed-character choice instead of clearing `shownCharacterId`.
14. **Given** a player becomes a character earlier in tonight's order than the
    Pit-Hag, **then** the rebuilt night sheet flags that row as newly added.
