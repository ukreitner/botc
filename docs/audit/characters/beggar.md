# Beggar (beggar) — Trouble Brewing Traveller

## Official rules (sources)

Sources (fetched 2026-08-25):
- <https://wiki.bloodontheclocktower.com/Beggar>
- <https://wiki.bloodontheclocktower.com/Travellers>
- <https://wiki.bloodontheclocktower.com/Glossary> ("Vote token: The round white
  circular token that is put on a player's life token when they die.")
- Official rulebook, "Travelers" chapter (mirror
  <https://www.web3us.com/sites/default/files/Rulebook.pdf>).

Current ability text (wiki):

> "You must use a vote token to vote. If a dead player gives you theirs, you learn
> their alignment. You are sober and healthy."

`characters.json` has the older phrasing: "You must use a vote token to vote. Dead
players may choose to give you theirs. If so, you learn their alignment. You are
sober & healthy." Same rules, **drifted wording** — worth updating.

How to run (wiki, near-verbatim):

- "**The Beggar cannot vote without a vote token, losing one each time they vote.
  During the day, dead players may declare they give their vote token to the
  Beggar. Transfer the token and privately inform the Beggar of that player's
  alignment.**"
- Key mechanics:
  - "**Only dead players may give vote tokens to the Beggar.**"
  - "**Beggars learn the alignment of players donating tokens.**"
  - "**Beggars cannot become drunk or poisoned.**"
  - "**Nominating and voting for exiles are unaffected by this ability.**"
  - "**Each dead player decides independently whether to donate.**"
- Examples:
  - "A Beggar receives a vote token from the Monk (dead player) on day four and
    learns the Monk's alignment."
  - "When a good Beggar with three tokens receives one from the Recluse, they
    learn the Recluse is **evil**." — the donation reports **registration**, so a
    Recluse registers as evil and a Spy registers as good; the Storyteller may
    also choose either for misregistering characters.
  - "**If the Beggar dies that day, they lose all previous tokens but gain one to
    use while dead.**" — a dead Beggar has an ordinary single ghost vote, and any
    hoarded donations are discarded.
- Strategy notes that imply the ST must track the count: "Good Beggars should
  limit token collection to avoid removing good voters"; "Use acquired tokens
  decisively, especially on final days" — the number of tokens the Beggar holds is
  live, public-ish information the ST is expected to arbitrate.

Traveller framework (rulebook, verbatim):

- "**Choose Alignment. Tell the Traveler player in private whether they are good
  or evil. If you made the Traveler evil, they learn which player is the Demon…**"
  and "**Inform Group… (Do not declare their alignment.)**"
- "Travelers… may nominate, may vote… **lose their abilities when dead or drunk
  or poisoned**, and even get a vote token when they die." — for the Beggar the
  "sober & healthy" clause overrides the drunk/poisoned half; death still ends
  the token-collecting.
- Exile: "**Any player, even dead ones, may support the exile… Dead players that
  support an exile do not lose their vote token.**" · "**The process to exile a
  Traveler is not affected by abilities.**" · "**If at least half of the players
  support the exile, it succeeds**… total number of players in the game."

Jinxes: none for the Beggar in `night_and_jinxes.json` or on the wiki.

Night order: the Beggar never acts at night; correctly absent from both lists.

Note on the donating dead player: giving the token away spends it. A dead player
who donates has no ghost vote left for themselves.

## What the app does today

Data
- `characters.json` — `beggar`, team `traveler`, ability text as quoted (older
  phrasing), `reminders: []`, no night reminders.
- `night_guide.json` — **no `beggar` entry**.
- `night_and_jinxes.json` — absent from both orders. Correct.

Engine
- `beggar` appears in `engine/src/test/.../FullGamePlaytestTest.kt:210` and
  `GameActionsTest.kt:112` only as a convenient traveller for exile/deal tests.
  **No production code references it.**
- Vote tokens: `Player.ghostVoteUsed` (`GameState.kt:31`) is a single boolean per
  seat. There is no notion of a token *count*, and no notion of a token belonging
  to someone else. `GameActions.toggleGhostVote` (`GameActions.kt:183-184`) flips
  it. `GameActions.kill` (`GameActions.kt:144-145`) resets it to `false` on death.
- `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) returns true for **any**
  player carrying a reminder whose label contains "poison" or "drunk". There is no
  "sober & healthy" exemption for the Beggar (or for the Sailor, Goon etc.).

UI
- `DayScreen.kt:184` — `val canVote = p.alive || !p.ghostVoteUsed || isExile`. An
  **alive** player is always votable, so an alive Beggar with zero tokens can be
  tapped into any tally.
- `DayScreen.kt:232-240` — only *dead* voters' ghost votes are spent on Record.
- There is no way to record "the dead Monk gave their token to the Beggar", and no
  automatic alignment reveal. The ST would have to: manually mark the Monk's ghost
  vote used (`SeatSheet.kt:172-175` "Use ghost vote"), write "gave token to
  Beggar" in the Beggar's seat note (`SeatSheet.kt:243-248`), and separately open
  menu → "Show a card…" → Signals → Good/Evil (`ShowCards.kt:389-391`).
- The grimoire paints an impaired badge on any seat with a Drunk/Poisoned token
  (`GrimoireScreen.kt:344,428-440`), including the Beggar.
- Alignment defaults to good like every traveller (`Character.kt:16`,
  `GameState.kt:45-51`); bare "Flip alignment" (`SeatSheet.kt:315`).

Storyteller experience today: the Beggar's entire ability is invisible to the app.
The ST must remember how many tokens the Beggar holds, refuse or permit their
votes by memory, remember which dead players have donated (and that those donors
can no longer vote), remember to reveal the donor's alignment privately, remember
that a Recluse donor registers as evil, remember to wipe the Beggar's hoard if
they die, and remember that none of this constrains exile support.

## Defects and gaps

1. **P0** · The Beggar can vote with no token · Rules: "The Beggar cannot vote
   without a vote token." App: any alive player is an enabled voter chip ·
   `DayScreen.kt:184` · Repro: put a Beggar in play, open any nomination, tap the
   Beggar — the tally increments with nothing to stop it.

2. **P0** · There is no vote-token model at all · Rules: the Beggar accumulates
   tokens, spends one per vote, and loses the hoard on death ("they lose all
   previous tokens but gain one to use while dead"). App: `ghostVoteUsed` is one
   boolean, meaningful only for dead players · `GameState.kt:31`,
   `GameActions.kt:144-145,183-184`.

3. **P1** · No way to record a donation · Rules: "dead players may declare they
   give their vote token to the Beggar. Transfer the token and privately inform
   the Beggar of that player's alignment." App: nothing. The ST must hand-edit the
   donor's ghost vote and hand-show an alignment card from a different menu ·
   `SeatSheet.kt:172-175`, `ShowCards.kt:389-391`.

4. **P1** · The alignment reveal is neither computed nor prompted · The app knows
   every player's alignment (`Player.isEvil`, `GameState.kt:45-51`) and has a
   full-screen GOOD/EVIL card (`ShowCards.kt:105-127`), but nothing joins the two
   at the moment of donation, and nothing applies **misregistration** (Recluse
   registers evil, Spy registers good — the wiki's own example) · `InfoCalc.kt`
   has misregistration handling for info roles but no entry point here.

5. **P1** · "You are sober & healthy" is not modelled · Rules: "Beggars cannot
   become drunk or poisoned." App: a Poisoner/Sailor/Innkeeper/Sweetheart token
   dropped on the Beggar marks them impaired everywhere
   (`StatusEffects.kt:36-46`), paints the poison badge
   (`GrimoireScreen.kt:428-440`) and would suppress their ability in any future
   ability-gating code · Repro: night tray → Poisoner "Poisoned" → tap the Beggar
   → green impaired dot appears.

6. **P1** · Donor bookkeeping is not automatic · A dead player who donates has
   spent their vote for the game. Nothing links the two seats, so the ST can
   accidentally let the donor vote later · `DayScreen.kt:184`.

7. **P2** · The Beggar's death does not clear their hoard · Rules: "If the Beggar
   dies that day, they lose all previous tokens but gain one to use while dead."
   App: nothing to clear, but the future implementation must do this in
   `GameActions.kill`.

8. **P2** · Exile exemption must be explicit · Rules: "Nominating and voting for
   exiles are unaffected by this ability." Today the exile path ignores tokens
   anyway (`DayScreen.kt:184` `|| isExile`), so this is currently right by
   accident; it must stay right once tokens exist.

9. **P2** · No day-start briefing · Nothing tells the ST "Beggar holds 2 tokens"
   at the start of the day, which is the number they must police all day ·
   `DayScreen.kt:85-124`.

10. **P2** · The Beggar has no reminder tokens in `characters.json`, so there is
    no in-grimoire representation of a held token even for a manual ST.

11. **P3** · Ability text drift vs the wiki ("Dead players may choose to give you
    theirs. If so, …" vs "If a dead player gives you theirs, …") ·
    `characters.json` `beggar.ability`.

12. **P3** · No day-guide entry, so the Beggar's how-to-run text is nowhere in the
    app.

## Proposed behaviour (spec)

### Engine: generalise the vote token

Replace the single boolean with a count, keeping the old field for save
compatibility or migrating it:

```kotlin
// GameState.Player
val voteTokens: Int = 0,        // spendable vote tokens held right now
```

- Living non-Beggar players: `voteTokens` is irrelevant; they vote freely.
- On death (`GameActions.kill`, `GameActions.kt:136-156`): set `voteTokens = 1`
  (this replaces `ghostVoteUsed = false`) — "even get a vote token when they die".
  **For a Beggar specifically, set `voteTokens = 1` regardless of the hoard**
  ("they lose all previous tokens but gain one to use while dead").
- `revive`/`resurrect` (`GameActions.kt:162-181`): set `voteTokens = 0` for a
  non-Beggar (alive players hold no token), and for a living Beggar keep whatever
  donations they hold.
- Migration: `ghostVoteUsed == false && !alive` → `voteTokens = 1`; otherwise 0.
  Keep a derived `val ghostVoteUsed get() = !alive && voteTokens == 0` so
  `SeatSheet.kt:176-178` and `GrimoireScreen` keep compiling.

### Engine: donation action

```kotlin
/**
 * A dead player gives their vote token to the Beggar. The donor spends their
 * token; the Beggar gains one; the Beggar privately learns the donor's
 * alignment as it registers.
 */
fun donateVoteToken(state: GameState, donorId: Long, beggarId: Long): GameState
```

Guards: donor is dead and `voteTokens >= 1`; recipient is an alive Beggar.
Effects: donor `voteTokens -= 1`; Beggar `voteTokens += 1`; append a
`PlacedReminder("beggar", "Token from <DonorName>")` on the Beggar's seat (one per
donation, non-exclusive, so the count is visible in the grimoire); log the
donation.

### Engine: what the Beggar learns

New `InfoCalc` entry `beggar` (targets 1 = the donor):

- True answer: the donor's alignment **as they register**:
  - Recluse → may register evil (default **evil**, per the wiki's own example);
  - Spy → may register good (default **good**);
  - both storyteller-overridable with a two-button choice, as `InfoCalc` already
    does for other misregistering reads;
  - `alignmentFlipped` seats (Bounty Hunter target, converted Snake Charmer,
    Marionette etc.) use their **current** alignment.
- The Beggar is **sober & healthy**, so there is **no** drunk/poisoned false
  answer and **no Vortox override** — the answer is always true (subject to
  misregistration). State this as a caveat line in the panel.
- Output: a `ShowCard.AlignmentCard(evil = …)` chip, reusing
  `ShowCards.kt:105-127`.

### Vote rules integration

Extend the shared `VoteRules` object described in `voudon.md`:

- `isExile == true` → the Beggar is eligible like anyone else, spends nothing
  ("Nominating and voting for exiles are unaffected by this ability").
- Otherwise, for an **alive Beggar**: eligible only if `voteTokens >= 1`; on
  Record, decrement by 1 and remove one `Token from …` reminder.
- For a **dead Beggar**: ordinary ghost-vote rules (`voteTokens >= 1`, spent on
  use).
- Disabled chip reason: `Beggar — no vote token`.

### Impairment exemption

`StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) must return `false` for a
player whose `characterId` is in a `SOBER_AND_HEALTHY` set (`beggar`, and the
same mechanism serves `sailor`'s "can't die" cousins and any future
"you are sober & healthy" character). Keep the reminder token placeable — a
Poisoner may still *choose* the Beggar, and the ST wants to see that they did —
but do not derive impairment from it, do not paint the badge, and add a note on
the seat: "Beggar is sober & healthy — this token has no effect."

### Day-start briefing (shared panel)

> **Beggar in play — holds 2 vote tokens** (from *Monk†*, *Recluse†*).
> They may vote twice today and no more. Dead players may hand over their token at
> any time during the day; when they do, show the Beggar that player's alignment
> privately. The Beggar can support exiles freely without a token.
> Reminder: the Beggar cannot be drunk or poisoned.

### Day-time inputs the app must record

- **Donation** (donor → Beggar), any time during the day: a one-tap action from
  the Beggar's seat sheet ("A dead player gives their token…" → list of dead
  players holding a token) *and* from any dead player's seat sheet ("Give vote
  token to the Beggar"). Both call `donateVoteToken` and immediately offer the
  alignment card.
- Log entries: `"Monk gave their vote token to the Beggar (day 4) — shown EVIL"`
  (the shown value, so the ST can defend the ruling later).

### Interactions to handle explicitly

- **Recluse / Spy** — misregistration on the donation reveal, with an ST choice.
- **Voudon** — an alive Beggar cannot vote at all while a Voudon is active
  (they are alive and not the Voudon); a dead Beggar votes freely and spends
  nothing. Donations are still possible but pointless; say so in the panel.
- **Bureaucrat / Thief** — a Beggar's vote can be tripled or negated like anyone
  else's; the token is spent either way.
- **Gunslinger** — a Beggar who spends a token to vote is "a player that voted"
  and may be shot.
- **Exile** — never token-gated (see above).
- **Butler** — irrelevant; the Beggar's restriction is their own.
- **Vortox / poisoning** — no effect on the Beggar's information.

### UI text

- Seat sheet (Beggar): `Vote tokens: 2 — from Monk†, Recluse†`
- Donation button (dead seat): `Give vote token to the Beggar`
- Reveal chip: `Show the Beggar: Monk is EVIL`
- Disabled voter chip: `Beggar — no vote token`
- Poison token note: `Beggar is sober & healthy — no effect`

### Data changes

- `characters.json`: update `beggar.ability` to the wiki's current text; add
  `reminders: ["Token"]` (or rely on the dynamic `Token from <name>` label above —
  prefer a static `"Token"` label plus the donor name in the seat note, so the
  token art stays legible).
- Add a day-guide entry for `beggar`.

## Tests to add

1. `Given` an alive Beggar with `voteTokens == 0`, `when` `voteRules` is computed
   for a non-exile nomination, `then` the Beggar is **not** in
   `eligibleVoterIds`.
2. `Given` the same Beggar, `when` the nominee is a traveller (exile), `then` the
   Beggar **is** eligible and `spendsGhostVotes == false`.
3. `Given` a dead Monk with `voteTokens == 1` and an alive Beggar, `when`
   `donateVoteToken(state, monkId, beggarId)`, `then` the Monk has 0 tokens, the
   Beggar has 1, and the Beggar's seat carries one `beggar` reminder naming the
   Monk.
4. `Given` the Beggar votes on an execution with 1 token, `when` the nomination is
   recorded, `then` the Beggar has 0 tokens and one donation reminder is removed.
5. `Given` a Beggar holding 3 tokens, `when` `GameActions.kill(beggar, DEMON)`,
   `then` the Beggar has exactly `voteTokens == 1` and no donation reminders.
6. `Given` a dead **Recluse** donating to the Beggar, `then`
   `InfoCalc.compute(..., "beggar", beggarId, listOf(recluseId))` returns EVIL by
   default and offers the good/evil override.
7. `Given` a dead **Spy** donating, `then` the default answer is GOOD.
8. `Given` a Beggar carrying a `PlacedReminder("poisoner","Poisoned")`, `then`
   `StatusEffects.isImpaired(state, lookup, beggar) == false`.
9. `Given` a Beggar and a Vortox in play, `then` the Beggar's donation reveal is
   still the true (registered) alignment.
10. `Given` a Voudon in play and an alive Beggar with 2 tokens, `then` the Beggar
    is not in `eligibleVoterIds` for an execution vote and keeps both tokens.
