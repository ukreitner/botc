# Dreamer (dreamer) — Sects & Violets Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Dreamer> (fetched 2026-08-25);
Vortox rule from <https://wiki.bloodontheclocktower.com/Vortox>.

Current ability text:

> "Each night, choose a player (not yourself or Travellers): you learn 1 good & 1 evil
> character, 1 of which is correct."

**How to Run (verbatim):**

> "Each night, wake the Dreamer. They point at any player. If the chosen player's
> character is a Townsfolk or Outsider, show their character token and any Minion or
> Demon token to the Dreamer. If the chosen player's character is a Minion or Demon,
> show their character token and any Townsfolk or Outsider token to the Dreamer. Then,
> put the Dreamer to sleep.
>
> If the Dreamer chooses an evil player, you can help the evil team if you show the
> Dreamer the good character that this evil player is bluffing as, or if you show a
> more secretive character such as the Snake Charmer, Sage, Mutant, or Klutz."

**Examples (verbatim):**

> "The Dreamer chooses a player who is the Mutant. The Dreamer learns that this player
> is either the Mutant or the Cerenovus.
>
> The Dreamer chooses a player who was the Philosopher but gained the Flowergirl ability
> earlier that night. The Dreamer learns that this player is either the Philosopher or
> the Vigormortis.
>
> Today, both the Evil Twin and the Artist claimed to be the Artist. That night, the
> Dreamer chooses the player who is the Evil Twin. If the Storyteller wanted to help the
> good team, they could show the Evil Twin and the Sweetheart. But the Storyteller
> decides to help evil, so they show the Evil Twin and the Artist to the Dreamer.
>
> **The Dreamer chooses a player who is the Vortox. The Dreamer's information must be
> false because the Vortox is in play, so the Dreamer learns that this player is either
> the Oracle or the No Dashii.**"

**Storyteller-relevant timing / edge cases distilled from the above**

- **Every night, first and other.** Both nights, alive only.
- **Target constraints: not themselves, not a Traveller.** Dead players *are* legal
  targets (the ability says only "not yourself or Travellers").
- **The pair is by character TYPE, not alignment.** "Good character" means a Townsfolk or
  Outsider token; "evil character" means a Minion or Demon token. An evil-aligned
  Townsfolk (Bounty Hunter's evil Townsfolk, a Mezepheles turn, a Cult Leader flip) is
  still shown with a Townsfolk token as the "good" half.
- **The token shown is their CHARACTER, not their current ability** (example 2: a
  Philosopher who gained the Flowergirl ability is still shown as the Philosopher).
- **The decoy is the storyteller's free choice** — it may be a not-in-play character, an
  in-play one, the bluff the target is using, or a "secretive" character. It is a
  deliberate lever on the game, not a formality.
- **Vortox (mandatory, example 4)**: with an alive Vortox the Dreamer's information must
  be false — **neither** token may be the target's real character, and the pair must
  still be one good + one evil token.
- **Drunk / poisoned Dreamer**: the storyteller *may* make both tokens wrong (or leave
  the true one in — it is discretionary).
- **Misregistration**: a Recluse may register as a Minion or Demon — so the "correct"
  token for a Recluse may legitimately be an evil one; a Spy may register as a Townsfolk
  or Outsider, so the "correct" token for the Spy may be a good one. Both are legal.
- **Jinxes: none.**

## What the app does today

| path | what it holds |
|---|---|
| `engine/src/main/resources/botc/data/characters.json:811-822` | Ability text matches; first/other night reminders both "The Dreamer points to a player. Show 1 good and 1 evil character token; one of these is correct." `reminders: []`. Correct. |
| `engine/src/main/resources/botc/data/night_and_jinxes.json:350` (first, idx 55) and `:449` (other, idx 76) | Both night orders. Correct positions (after Clockmaker on night 1; after Undertaker, before Flowergirl on other nights). |
| `engine/src/main/resources/botc/data/night_guide.json:413-448` | Identical `first` and `other` prose plus **two** `"kind": "token", "token": "pick"` show cards, "Good candidate" and "Evil candidate", both with placeholder text "This player might be…". |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:24` | `targetsNeeded("dreamer") == 1`. |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:344-354` | The calculation. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:841-861` | The target picker: a FlowRow of `state.players` — **all** seats, unfiltered, unsorted. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:364-454` | `GuideShowDialog` — the "pick" token cards; editable text plus a character picker sorted with in-play characters first. |
| `engine/src/test/kotlin/com/clocktower/engine/InfoCalcTest.kt:146-163` | Existing test: rejects stale/duplicate/extra target selections. |

The calculation (`InfoCalc.kt:344-354`):

```kotlin
val target = validTargets(ctx, targets, 1)?.single() ?: return InfoResult("Pick the player the Dreamer chose")
val character = ctx.character(target)
val good = character?.team?.isEvil == false
return InfoResult(
    headline = "${ctx.name(target)} is the ${character?.name ?: "?"}",
    detail = "Show that token plus 1 ${if (good) "evil" else "good"} character token of your choice",
    caveats = misregistrations(ctx, listOf(target)),
)
```

**What already works — one line each:**

- Night order (both nights) and target count are correct.
- The good/evil split is computed from the character's **team**, not alignment
  (`character?.team?.isEvil`), which is the correct rule.
- The token shown is the target's `characterId`, i.e. their character, not any gained
  ability — matching the Philosopher example.
- Spy/Recluse presence on the *chosen* seat produces a caveat.
- The two "pick" show cards let the storyteller flash either token full-screen with an
  in-play-first character picker.

**Storyteller's experience today:** expand the Dreamer row, tap one of the eleven seat
chips (nothing stops you tapping the Dreamer's own seat or a Traveller), read
"Bo is the Vigormortis / Show that token plus 1 good character token of your choice",
then open two separate dialogs to show two separate full-screen cards, choosing the
second character yourself from a 24-item list with no suggestion. If the Dreamer is
poisoned or a Vortox is in play you get a red line and **no help at all** — the
false-info block only fires for numeric and yes/no answers.

## Defects and gaps

1. **P0 · The guide tells the storyteller a Vortox lie is optional.**
   `night_guide.json:413-448` (both `first` and `other`): *"If the Dreamer is drunk or
   poisoned, or the Vortox is in play, **neither token needs to be correct**."* The
   official rule is the opposite for the Vortox: "The Dreamer's information **must** be
   false because the Vortox is in play". A storyteller following the app's own text can
   legally-looking show the true character under a Vortox and break the game's central
   Vortox tell. **Repro:** S&V with Vortox + Dreamer, expand the Dreamer step, read the
   italic guide text.

2. **P0 · No false-pair help when impaired or under a Vortox.**
   The false-info block (`NightScreen.kt:903-930`) only triggers for a headline that
   starts with a digit or "YES"/"NO". The Dreamer's headline is "Bo is the Vigormortis",
   so `leadingNumber == null` and `isYes/isNo == false` — the entire "False info to show
   instead" section is skipped, even though the red caveat above it says the info must be
   false. The storyteller is told to lie and given nothing to lie with. **Repro:** poison
   the Dreamer, expand the step, pick a target: red caveat, zero chips.

3. **P1 · The target picker allows illegal targets.**
   `NightScreen.kt:846-860` lists every seat with no filter. The Dreamer may not choose
   themselves or a Traveller. Selecting the Dreamer's own seat produces "Marta is the
   Dreamer" and a nonsense pair. **Repro:** expand the Dreamer step, tap the Dreamer's
   own name.

4. **P1 · The decoy token is never proposed.**
   The How-to-Run explicitly makes the decoy a strategic lever ("show the good character
   this evil player is bluffing as… or a secretive character such as the Snake Charmer,
   Sage, Mutant, Klutz"). The app knows the demon bluffs (`GameState.demonBluffIds`), who
   is in play, and who is not — and offers none of it. The storyteller scrolls a generic
   alphabetical list (`NightScreen.kt:406-434`).

5. **P1 · Misregistration is prose, not options.**
   `misregistrations(ctx, listOf(target))` (`InfoCalc.kt:352`) emits "Priya is the Recluse
   — may register as evil / a Minion or Demon." It does not offer the *legal alternative
   pair* (e.g. "Recluse + Imp" where the Imp is the 'correct' half). Same for a chosen
   Spy.

6. **P1 · Nothing is recorded.**
   Which two tokens were shown, on which night, to whom, matters for: consistency across
   nights (the Dreamer's whole strategy), spotting your own mistakes, the Mathematician's
   malfunction ledger, and end-of-game reconstruction. The app stores nothing — the game
   log (`GameExtras.kt:44-106`) is deaths + nominations only.

7. **P2 · Both night guides are byte-identical and both say "one of these is correct"
   without stating the type rule.**
   The prose says "one good character token and one evil character token" but never says
   *which one is the true one is determined by the target's type*, nor that "good/evil"
   here means Townsfolk-or-Outsider vs Minion-or-Demon (not alignment). A storyteller
   with a Bounty-Hunter evil Townsfolk or a flipped Cult Leader target will guess wrong.

8. **P2 · The `detail` wording says "evil character token" where the rule says
   "Minion or Demon token".** `InfoCalc.kt:351`. With alignment flips in play this is
   actively misleading.

9. **P2 · Two separate show cards, two dialogs.**
   `night_guide.json:413-448` declares two independent `pick` shows; each opens its own
   `GuideShowDialog` (`NightScreen.kt:818-830`) and each replaces the full-screen card. The
   Dreamer is shown two tokens *together* at the table; a paired card would be one tap.

10. **P2 · Dead Dreamer / dead target.**
    A dead Dreamer gets only the generic "All holders are dead — usually skip"
    (`NightScreen.kt:751-757`). A dead *target* is legal and should be allowed, but the
    picker gives no hint that it is.

11. **P3 · `validTargets` failure text.** "Pick the player the Dreamer chose"
    (`InfoCalc.kt:346`) is fine; but the panel shows it as the gold headline, which reads
    like an answer. Minor.

## Proposed behaviour (spec)

- **when:** `both` (first and other night).
- **wake condition:** holder is **alive**.
- **targets:** exactly **1**.
  - constraint: `id != holder.id` and `!isTraveller`. Dead seats **are** legal.
  - picker default/sort: alive first, then dead; illegal seats rendered disabled with the
    reason on long-press ("the Dreamer can't choose themselves" / "Travellers can't be
    chosen").
- **immediate effects:** none — no tokens, no status effects, no kills.
- **deferred effects:** none.
- **expiry:** nothing to expire.
- **information (structured):** replace the string headline with

  ```kotlin
  Answer.CharacterPair(
      trueId: String?,          // target's characterId
      trueIsGoodHalf: Boolean,  // target.team is TOWNSFOLK/OUTSIDER
      decoyId: String?,         // storyteller's pick, persisted
  )
  ```
  Validity rule the engine enforces and the UI shows: the pair must contain **exactly one**
  Townsfolk/Outsider token and **exactly one** Minion/Demon token.
  - `obligation == TRUTH` or `MAY_LIE` (storyteller chooses truth): `trueId` must be one
    of the two.
  - `obligation == MUST_LIE`: **neither** token may equal `trueId`; the pair must still be
    one good + one evil token. The panel must refuse (or loudly warn on) a pair
    containing the true character.

- **decoy suggestions, ranked** (this is the main new value):
  1. if the target is evil: the character they are **bluffing** as — i.e. any
     `state.demonBluffIds` entry, and any character the target has been recorded claiming
     (see the `DayAct(kind = CLAIM)` model in `artist.md`);
  2. the four "secretive" characters named by the wiki when they are on the script:
     Snake Charmer, Sage, Mutant, Klutz;
  3. not-in-play characters of the required half;
  4. in-play characters of the required half.
  Present as chips with a one-word rationale ("bluff", "secretive", "not in play").

- **impaired / false alternative:** with `MUST_LIE` (Vortox) the panel presents a
  **false pair builder**: pick any good token ≠ trueId and any evil token ≠ trueId, with
  the same ranked suggestions; the true character is excluded from both lists and shown
  greyed with "excluded — Vortox". With `MAY_LIE` both modes are offered, truth first.

- **misregistration handling:** when the target is a Recluse, additionally offer the
  "registers as evil" pair — a Minion/Demon token as the *correct* half plus a
  Townsfolk/Outsider decoy — labelled "if the Recluse registers as evil". Symmetrically
  for a chosen Spy. When the target's `alignmentFlipped` is set, add a note that the type
  rule ignores alignment.

- **visibility:** nothing shown to Demon/Minions/Lunatic.
- **day-time inputs:** none required, but the Dreamer's decoy chooser should read the
  shared `DayAct(kind = CLAIM)` log so "who claimed what today" is one tap away.
- **record:** append a `NightAct(cycle, stepId = "dreamer", holderId, targetId,
  shownIds = [goodId, evilId], obligation)` to a new `GameState.nightActs` list, rendered
  in the game log ("N3 · Dreamer Marta chose Bo → shown Vigormortis + Artist").
- **interactions:** no jinxes. Vortox / drunk / poison / Spy / Recluse / alignment flips
  as above. Philosopher and Cannibal holders reach this step through
  `Player.nightRoleId`; the token shown is always the *target's* `characterId`.

### UI text the step should display

> **Dreamer — they point at a player (not themselves, not a Traveller).**
> Show one Townsfolk/Outsider token and one Minion/Demon token. One of them is the truth.
>
> **Bo is the Vigormortis** — a Minion/Demon, so the *evil* half is true.
> Pick the good decoy: `[ Artist · bluff ] [ Snake Charmer · secretive ] [ Sage · secretive ] [ more… ]`
> `[ Show both tokens full-screen ]`

Under a Vortox, replace the first line with:

> **VORTOX — this information MUST be false. Neither token may be Vigormortis.**
> Pick one good and one evil token, both wrong:
> `[ good: Oracle ] … [ evil: No Dashii ] …`

### Data changes

- `night_guide.json:413-448` — rewrite both `first` and `other` instructions:
  > "Wake the Dreamer. They point to any player other than themselves or a Traveller
  > (dead players are allowed). If that player is a Townsfolk or Outsider, show their real
  > character token plus **any Minion or Demon token**; if they are a Minion or Demon, show
  > their real token plus **any Townsfolk or Outsider token**. 'Good' and 'evil' here mean
  > the character's type, not their alignment. If the Vortox is in play the information
  > **must** be false — show two tokens, neither of which is their character. If the
  > Dreamer is drunk or poisoned you **may** give a false pair. Put them back to sleep."
- Replace the two `pick` shows with one paired show card, e.g.
  `{"label":"Show both tokens","kind":"tokenPair","text":"This player is one of these","token":"pick2"}`,
  and extend `GuideShow`/`ShowCard` (`NightGuide.kt:22-27`, `ShowCards.kt:65-77`) with a
  two-token card.
- `characters.json:811-822` — no change; optionally tighten
  `first/otherNightReminder` to "…Show 1 Townsfolk/Outsider and 1 Minion/Demon token…".

## Tests to add

1. **Good target → evil decoy required.**
   *Given* the Dreamer chooses a `chef`, *then* the result reports `trueIsGoodHalf = true`
   and the decoy list contains only Minion/Demon characters.

2. **Evil target → good decoy required (wiki example 1).**
   *Given* the Dreamer chooses a `mutant`, *then* the true half is the good half and the
   decoy list is Minion/Demon (matching "Mutant or Cerenovus").

3. **Type, not alignment.**
   *Given* the Dreamer chooses a Townsfolk with `alignmentFlipped = true`,
   *then* the true token is still the good half. (Currently passes — lock it in.)

4. **Ability gained ≠ character shown (wiki example 2).**
   *Given* the target is a `philosopher` carrying a Flowergirl-ability marker,
   *then* the true token is `philosopher`.

5. **Vortox forbids the true token (currently fails).**
   *Given* an alive `vortox` and any target, *then* `obligation == MUST_LIE` and a
   candidate pair containing the target's real character is rejected by the validator.

6. **Poison permits but does not require a lie.**
   *Given* a poisoned Dreamer and no Vortox, *then* `obligation == MAY_LIE` and both a
   truthful and a false pair validate.

7. **Illegal targets rejected (currently fails).**
   *Given* targets = [the Dreamer's own id], *then* the result is the "can't choose
   yourself" message, not a character pair. Same for a `isTraveller = true` seat.

8. **Dead target allowed.**
   *Given* a dead non-Traveller target, *then* a normal pair is produced with no error.

9. **Recluse alternative pair (currently fails).**
   *Given* the target is the `recluse`, *then* the result offers a second legal pair in
   which a Minion/Demon token is the true half, attributed to the Recluse.

10. **Decoy suggestions are ranked (currently fails).**
    *Given* `demonBluffIds = ["artist","juggler","mutant"]` and an evil target,
    *then* `artist` and `juggler` appear in the good-decoy suggestions before
    not-in-play characters that are not bluffs.

11. **Pair validity.**
    *Given* a candidate pair of two Townsfolk tokens, *then* the validator rejects it
    ("one Townsfolk/Outsider and one Minion/Demon").

12. **Night record written.**
    *Given* the storyteller confirms a pair, *then* `nightActs` gains one entry for that
    cycle naming holder, target and both shown ids.
