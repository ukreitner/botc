# Wizard (wizard) — exp (Carousel) minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Wizard>

Current ability text:

> "Once per game, choose to make a wish. If granted, it might have a price &
> leave a clue as to its nature."

`characters.json:1920` matches this text exactly — **no drift**.

### How to run (wiki, verbatim, in order)

- "When the Wizard makes a wish, either verbally or via text, decide whether to accept or decline the wish."
- "If the wish is declined, prompt the Wizard to wish again, or tell them that they have no more wishes."
- "If the wish is granted, say 'Your wish is granted.' or 'Your wish is my command', or nod, or otherwise signal that their wish is accepted."
- "Now or later, you may make a price: make whatever mechanical adjustments to the game you feel are necessary."
- "Now or later, you may declare publicly that the Wizard has made a wish, then give the good team a clue."

Storyteller guidance from the same page: aim to "grant all wishes, all wishes leave a clue, and all wishes have a price" where possible, while keeping the good team's chance of winning intact.

### Examples (verbatim)

- "The Wizard wishes to see the Grimoire. The Storyteller grants this wish, and there is no price and no clue."
- "The Wizard wishes that all good players are drunk. The Storyteller grants the wish." (clue: "Things are wrong"; the good players then receive false information.)
- "The Wizard wishes that they become a Demon. The Storyteller grants the wish." (clue: "The student has become the master"; in the worked example the Lord of Typhon is killed and the Wizard becomes the Ojo.)
- "The Wizard wishes to win the game. The Storyteller grants the wish, and tells the Wizard that the evil team will win." (the evil team wins at day's end; the good team gets a clue identifying possible Demons.)
- "The Wizard wishes that all players have 5 lives… The Storyteller judges that this wish is too awkward… declines the wish."

### Key clarifications from Tips & Tricks (verbatim bullets)

- "As long as you've made a wish, the effects of your wish stay in play **even after you are dead**."
- "Be ready for the Storyteller to declare that the Wizard has made a wish, or for the Storyteller to make an 'unexpected' announcement."
- The wish may be communicated "either verbally or via text" — i.e. **the Wizard can wish at any time, including during the day**, by handing the Storyteller a note. The page does **not** designate a night or a phase, and does **not** say the Wizard wakes each night. I looked for such a sentence specifically and found none.
- "Make your wish immediately!" / "Wait a few days…" — the timing is entirely the player's choice.

### Jinxes

**None.** The wiki page has no Jinxes/Bootlegger section.

## What the app does today

Data paths — **this is the complete list**:
- `characters.json:1920` — text, `firstNightReminder` and `otherNightReminder` both "Do whatever needs to be done to satisfy the Wizard's ability.", reminders `["?", "?"]` (two tokens with the identical label `?`).
- `night_and_jinxes.json:325` (firstNight index 30, between `courtier` and `snakecharmer`) and `:389` (otherNight index 16, between `courtier` and `gambler`). Matches the official night order.
- `night_guide.json:1493` — first/other prose telling the Storyteller to establish a signal, hear the wish, decide grant/price/clue, and "use the '?' reminders to track its ongoing effects". Show card: `DO YOU WISH TONIGHT?` (`kind: "message"`).
- `raw_exp_evil_outsiders.json:318` — raw import copy.

No `wizard` reference exists in `engine/src/main/kotlin` or `app/src`.

Storyteller experience:
1. Every night the Wizard's step appears with the "Do whatever needs to be done…" text and the guide prose.
2. Because the ability string starts with "Once per game", the tray shows a **"Mark spent"** chip (`NightScreen.kt:204-220`) which places `PlacedReminder("wizard","No ability")` on the holder. That is a genuinely useful automatic behaviour and it **works**.
3. The two `?` reminder tokens are placeable from the tray. `allReminders` (`Character.kt:62`) yields `["?","?"]`, so `availableCopies == 2` and `NightScreen.kt:266-280` takes the multi-copy branch: two `?` tokens may exist at once, and a third placement removes the oldest. Functionally fine, but both tokens are literally labelled `?` on the seat with no way to say what they mean.
4. Nothing else. The wish text, the price, the clue, whether the clue was announced, and whether the wish was granted or declined all live in the Storyteller's head or in the single free-text `storytellerNotes` blob (`GameState.kt:112`, reachable via the "Storyteller notes" menu item at `GameShell.kt:222-225`).

## Defects and gaps

1. **P1 · There is nowhere to record the wish, the price or the clue** — this character's entire state is prose that the Storyteller must invent and then remember for the rest of the game, and the app offers one shared notes blob for the whole game. The user's Gossip complaint ("make it easy to write down all the gossips") is the same complaint in a different costume. `GameState.kt:112`, `GameShell.kt:222-225`.
2. **P1 · The clue is a public day-time announcement with no home** — "Now or later, you may declare publicly that the Wizard has made a wish, then give the good team a clue." Nothing schedules it, nothing reminds the Storyteller at day start that a clue is owed, and nothing records what was said so it can be repeated consistently later.
3. **P1 · The step keeps appearing after the wish is spent** — the wiki says a declined wish can be re-wished, but once a wish has been **granted** the Wizard never acts again. The app renders the step every night regardless of the `No ability` marker; `NightOrder.build` (`NightOrder.kt:120-179`) has no spent-check, and only the *tray chip* (`NightScreen.kt:204-206`) reacts to the marker.
4. **P1 · The wish can be made during the day and the app has no day-side entry point** — "either verbally or via text". A note passed at 11am has to be held until the Wizard's night step comes round, which is precisely when the Storyteller is busiest.
5. **P2 · The `?` reminder tokens carry no text** — `characters.json:1920` gives two tokens both labelled `?`, and `ReminderPicker`/the tray place them verbatim. There is no way to write "all good players are drunk" on one. The app needs **free-text reminder labels** (a general feature — the Wizard is the extreme case, but every improvised Storyteller ruling wants it).
6. **P2 · "Mark spent" is fired from a heuristic, not from the rules** — `oncePerGame = character.ability.startsWith("Once per game")` (`NightScreen.kt:204`). It happens to be right for the Wizard, but the chip does not distinguish *declined* (still available) from *granted* (spent), which is the one distinction this character actually has.
7. **P2 · Wish effects must survive the Wizard's death and nothing says so** — "the effects of your wish stay in play even after you are dead". The night sheet's generic "All holders are dead — usually skip." line (`NightScreen.kt:751-757`) is the opposite advice, and any `?` tokens on other seats have no owner-alive condition attached.
8. **P2 · No prompt for the "unexpected announcement"** — the wiki tells Storytellers to prepare the good team for an out-of-nowhere declaration; the app has a full-screen `ShowCard.Message` facility (`components/ShowCards.kt`, reachable via "Show a card…" at `GameShell.kt:226-229`) that could carry a saved clue with one tap.
9. **P3 · The guide's suggested wishes are not surfaced** — the wiki's five worked examples (see the Grimoire, all good players drunk, become a Demon, win the game, declined) are the exact menu a Storyteller wants at 1am. `night_guide.json:1493` mentions none of them.

## Proposed behaviour (spec)

The Wizard is the app's best argument for a small, general **"storyteller ruling" record** — a structured note attached to a character with a text body, an optional public clue, and a lifetime. Spec below assumes that.

### Night step

- **when**: both nights; wake condition = the Wizard has **not** yet had a wish granted (no `wizard`/`Wish granted` marker). Holder may be alive **or dead** — the Wizard can only *wish* while alive (Minion), but keep the step visible while alive only, and keep any granted-wish effects visible after death (below).
- **targets**: none by default. A granted wish may require targets; those are handled by whatever tokens/actions the Storyteller applies, not by the step.
- **immediate effects**: the step offers three actions:
  1. **`No wish tonight`** → just checks the step off.
  2. **`Wish declined`** → records the wish text in the wish log with `granted = false`; the step stays available on later nights ("prompt the Wizard to wish again"). Show card: `YOUR WISH IS DECLINED — WISH AGAIN?`
  3. **`Wish granted`** → opens the **Wish sheet**:
     - `What did they wish for?` (multiline text)
     - `Price (optional)` (multiline text) — "make whatever mechanical adjustments to the game you feel are necessary"
     - `Clue for the good team (optional)` (single line) + a checkbox `Announce publicly at the next day start`
     - a chip row of the wiki's worked examples as one-tap starting points: *See the Grimoire · All good players are drunk · Become a Demon · Evil wins today · Decline (too complex)*
     - On save: `addReminder(holder, PlacedReminder("wizard","Wish granted"))`, store the wish record, and show the card `YOUR WISH IS GRANTED.`
- **deferred effects**:
  - If `Announce publicly` was ticked, the **day-start briefing** carries `Announce: the Wizard has made a wish. Clue — "<clue text>"` with a one-tap `Show full-screen` using the existing `ShowCard.Message` path, and marks itself done once shown.
  - The wish's ongoing effects are tracked as `?` tokens **with the Storyteller's own label** (see data changes) and are listed in the day briefing as `Wish in effect: <wish text>` for the rest of the game, **including after the Wizard dies**.
- **expiry**: nothing expires automatically. The wish record and its tokens persist until the Storyteller clears them. `wizard`/`Wish granted` never expires.
- **information**: none computed.
- **visibility**: the wish itself is secret; the clue is public. The evil team may be told (Tips: "Tell the rest of your evil team what you wished for!") — offer a `Show to the evil team` card.
- **day-time inputs the app must let the Storyteller record**: a **"Wizard wished (note passed)"** action available from the Day screen at any time, opening the same Wish sheet. This is the fix for defect 4 and matches "either verbally or via text".

### Once the wish is granted

- `NightOrder.build` (`NightOrder.kt:120-179`) should skip a character's step when a **spent marker** for that character is present and the character's ability is once-per-game. Generalise: a `spentMarker` concept (label `No ability`, or a per-character marker) suppresses the row and replaces it with a single collapsed line `Wizard — wish already granted (<wish text>)`.
- The night sheet must **not** say "usually skip" for a dead Wizard whose wish is still in effect; instead: `Dead — but the granted wish stays in play.`

### UI text the step should display

- `Does the Wizard wish tonight?` → `[ No wish ] [ Wish declined ] [ Wish GRANTED… ]`
- Inside the sheet: `Grant all wishes if you can. Every wish should have a price and leave a clue — but keep good able to win.`
- Show cards: `YOUR WISH IS GRANTED.` / `YOUR WISH IS MY COMMAND.` / `YOUR WISH IS DECLINED — WISH AGAIN?`
- Day briefing: `Wizard's clue to announce: "<clue>"` and `Wish in effect: <wish>`

### Data changes

- `characters.json:1920`: keep the two `?` tokens, but the **reminder system** must support a free-text label. Concretely: `PlacedReminder` (`GameState.kt:7-11`) gains an optional `text: String = ""` used for display when `label == "?"`, and `ReminderPicker`/the night tray prompt for it. This is a general engine change; the Wizard is the driving case.
- `night_guide.json:1493`: rewrite both sections around the three actions above; add the five example wishes; add "the effects stay in play even after the Wizard is dead"; add the decline-and-re-wish rule; add a second show entry for the declined card.
- `night_and_jinxes.json`: no jinxes to add.
- New engine state: `GameState` gains a small `rulings: List<Ruling>` (sourceId, cycle, body, publicClue, announced) — or, minimally, a per-character notes map. The Wizard, the Gossip complaint from the user's report, and every improvised Storyteller ruling share it.

## Tests to add

1. **Spent marker suppresses the step** — *Given* the Wizard holds `wizard`/`Wish granted`, *When* `NightOrder.otherNight` is built, *Then* no interactive `wizard` step is returned (or it is returned flagged `spent = true`).
2. **A declined wish does not spend the ability** — *Given* a declined wish recorded on night 1, *When* night 2's sheet is built, *Then* the `wizard` step is present and offers the wish actions.
3. **Granted wish survives the Wizard's death** — *Given* a granted wish and then the Wizard is killed, *Then* the wish record is still present and flagged in-effect, and the day briefing still lists it.
4. **Wish recorded from the day phase** — *Given* `phase == DAY`, *When* the day-side "Wizard wished" action saves a granted wish, *Then* the marker and record exist and night 2's step is suppressed.
5. **Clue announcement is surfaced once** — *Given* a granted wish with `announcePublicly = true`, *When* the next day starts, *Then* the briefing contains the clue; *When* it is marked shown, *Then* it does not reappear the following day.
6. **Labelled `?` tokens round-trip** — *Given* `PlacedReminder("wizard","?", text = "All good players are drunk")`, *When* the state is serialised and reloaded, *Then* the text survives and the seat renders it.
7. **Two `?` tokens coexist** — *Given* two `?` placements on two different seats, *Then* both are present (guards the current `availableCopies == 2` behaviour at `NightScreen.kt:260-280` against a regression to exclusive placement).
