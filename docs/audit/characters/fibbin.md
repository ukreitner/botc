# Fibbin (fibbin) — fabled Fabled

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Fibbin> (fetched verbatim via
`api.php?action=parse&page=Fibbin&prop=wikitext`, 2026-08-25).

Current ability text (verbatim summary line):

> "Once per game, 1 good player might get incorrect information."

*(The wiki's summary line reads "incorrect information"; `characters.json` and the
community dataset both carry "false information". Same rule, different word — the app's
text is the older script-tool wording. See defect 8.)*

**Summary bullets (verbatim):**

- "Add the Fibbin if your script has too much information or no possibility of misinformation."
- "If you create a character list and it has no characters that cause drunkenness, poisoning, or other ways for information to be false, then you may want to add the Fibbin. Whilst it is not necessary, even a minor chance of a good player's information being incorrect can drastically help the evil players bluff."
- "**The Fibbin does not make an ability fail in the way that drunkenness and poisoning do. It only affects abilities that provide information from the Storyteller signaling to a player during the night or telling them something.**"
- "If the game ends before you have given a good player incorrect information, that's okay."
- "**Some characters get false information due to their ability. The Fibbin can make this information true.**"

**How to Run (verbatim):**

> At the start of the game, declare that the Fibbin is in play. Add the Fibbin token to the Grimoire.
>
> Once per game, when a good player gains information from an ability, you can give them incorrect information. **The Fibbin loses their ability**—put their **NO ABILITY** reminder token by their token.

**Examples (verbatim):**

1. "On the first night, all players get correct information. On the second night, the Empath learns they are neighbouring one evil player, but both their neighbours are actually good. For the rest of the game, all good players get correct information."
2. "The Virgin is nominated by a Townsfolk. This Townsfolk is executed immediately because the Fibbin cannot make an ability malfunction. Later, the Monk protects a player. Again, the Monk's ability cannot fail due to the Fibbin ability. Later, the Ravenkeeper dies at night and gets false information, because information from an ability can be affected by the Fibbin ability."

**Rules distilled:**

| | |
|---|---|
| Scope | **Information only.** Not "the ability malfunctions". The Virgin still triggers, the Monk still protects, the Slayer still slays. |
| Target | A **good** player. |
| Frequency | **Once per game**, at the storyteller's discretion; may go unused. |
| Timing | Whenever a good player gains information from an ability — night info, first-night info, on-death info (the Ravenkeeper example), and verbal day info (Artist, Savant, Fisherman: "telling them something"). |
| Mark | **NO ABILITY** on the **Fibbin's own token** — i.e. the Fabled is spent, not the player. |
| Inversion | It can also make *naturally false* info **true** — e.g. a drunk or poisoned player, or a Vortox game, where the storyteller would have lied; the Fibbin's use can be to tell the truth instead. |

**Jinxes:** none.
**Night order:** never wakes. Correctly absent from both order lists.

## What the app does today

Data:
- `characters.json:2222-2235` — `ability: "Once per game, 1 good player might get false
  information."`, `team: fabled`, `setup: false`, `reminders: ["Used"]`, no night
  reminders. The wiki names the token **NO ABILITY**; the app (following the community
  dataset) names it `Used`.
- `night_and_jinxes.json` — correctly absent from both order lists. **Works.**
- `night_guide.json` — no entry. Correct for the night sheet; the Fibbin's whole
  How-to-Run is "watch every info step and consider lying once", which has no home.

Code — **zero** engine awareness. Everything below is what the storyteller has to do
manually, and the near-misses in the code that make it frustrating:

- `GameExtras.kt:167-195` `FabledSheet` toggles the id; nothing else.
- **Nowhere to put the token.** `PlacedReminder` (`GameState.kt:6-11`) lives on a
  `Player` (`GameState.kt:30`). The Fibbin has no seat, so "put their NO ABILITY reminder
  token by their token" has no representation at all. The storyteller must park a generic
  `"Used"` chip (`SeatSheet.kt:502`) on an unrelated seat, or write in the notes.
- **The Fibbin's own token isn't offerable anyway.** `ReminderPicker`
  (`SeatSheet.kt:498-500`) sources from `resolve(script)`, and built-in scripts exclude
  Fabled (`GameData.kt:39-42`); `NightToolTray` (`NightScreen.kt:202`) only offers the
  expanded night step's character's tokens, and the Fibbin has no step. So
  `characters.json:2232`'s `"Used"` is unreachable.
- **The "Mark spent" affordance exists and cannot see the Fibbin.**
  `NightScreen.kt:204` computes
  `oncePerGame = character?.ability?.startsWith("Once per game", ignoreCase = true)` and
  `NightScreen.kt:263-279` renders a "Mark spent" chip that places
  `PlacedReminder(character.id, "No ability")` — the exact token the wiki names. It is
  gated on `holders.isNotEmpty()` (`NightScreen.kt:263`), and a Fabled has no holders, and
  the Fibbin has no step to expand. The right mechanism is already built and structurally
  unreachable.
- **False info is only one tap away when the player is already impaired.**
  `StepDetailPanel` (`NightScreen.kt:836-930`) computes true info via `InfoCalc`, then at
  `NightScreen.kt:904-907`:
  ```kotlin
  val impaired = result.caveats.any {
      "POISONED" in it || "DRUNK" in it || "IS the Drunk" in it || "VORTOX" in it || "No Dashii" in it
  }
  ```
  and only then renders the "False info to show instead:" chips
  (`NightScreen.kt:908-928`, every number 0–4 except the truth, or the flipped YES/NO).
  For a **sober** Empath — the wiki's own example 1 — the chips do not appear, so the
  storyteller must open the generic "Show a card…" tool (`GameShell.kt:231-233`,
  `ShowToolSheet`) and hand-pick a number. The one moment the Fibbin exists for is the one
  moment the shortcut is hidden.
- **No spent tracking.** `GameState` has nowhere to record that the Fibbin has been used;
  the storyteller must remember across a two-hour game.
- **No reminder that it is available.** No info step mentions the Fibbin at all.
- Day-time info abilities (Artist, Savant, Fisherman) have no recording surface anywhere in
  the app, so the Fibbin cannot be applied to them through any UI.

## Defects and gaps

1. **P1** · The Fibbin cannot be marked as used. Its token (`characters.json:2232`,
   `"Used"`) is unreachable from every picker: `ReminderPicker` excludes Fabled on built-in
   scripts (`SeatSheet.kt:498-500` + `GameData.kt:39-42`), and `NightToolTray` needs a
   night step the Fibbin doesn't have (`NightScreen.kt:202`). The generic `"Used"` chip
   goes on a *player*, which is both wrong (the mark belongs to the Fabled) and confusing
   (it reads as that player's once-per-game being spent).
   *Repro:* Fabled… → Fibbin → open any seat → Add reminder → no Fibbin group; only the
   generic "Used" chip.
2. **P1** · No one-tap lie for a **sober** info player, which is the Fibbin's entire use
   case. `NightScreen.kt:904-907` gates the false-info chips on the *impairment* caveats.
   *Repro:* sober Empath on night 2 → expand the Empath step → the true number appears with
   a "Show N full-screen" chip, and no alternative numbers.
3. **P1** · No once-per-game state. Nothing in `GameState` (`GameState.kt:94-132`) can hold
   it, so the app cannot grey the option out afterwards, cannot warn "already used", and
   cannot show it in the log or reveal.
4. **P1** · The Fibbin is invisible at the moment of decision. Every `InfoCalc` step is an
   opportunity; none of them mentions that a Fibbin is in play and unspent. The storyteller
   must hold that fact in their head across every info wake of the game.
5. **P2** · The "can make naturally-false info true" half is entirely unsupported. When a
   player *is* impaired, the app tells the storyteller to lie
   (`NightScreen.kt:908-912`, "False info to show instead:") with no affordance for
   "use the Fibbin to tell the truth instead" — and no way to record that they did.
6. **P2** · Nothing distinguishes info-giving from ability-malfunction. Wiki example 2 is
   specifically about storytellers over-applying the Fibbin to the Virgin and the Monk;
   the app has no text anywhere saying "information only". A run-book line would prevent a
   real, documented mistake.
7. **P2** · No log entry. "N2: Fibbin used on the Empath (shown 1, truth 0)" is exactly the
   line a storyteller wants at the reveal, and the log (`GameExtras.kt:46-106`) has no
   Fabled or info events at all.
8. **P3** · Wording/token drift: `characters.json:2226` says "false information" (wiki:
   "incorrect information"); `characters.json:2232` names the token `"Used"` (wiki:
   **NO ABILITY**). Both are the older script-tool strings.
9. **P3** · The Fibbin only applies to **good** players; nothing in the app would stop a
   storyteller applying it to an evil info role (Widow, Spy grimoire view, evil Village
   Idiot), and no text says otherwise.

## Proposed behaviour (spec)

Shares the `FabledEntry` storage introduced in `angel.md`; the Fibbin uses
`used: Boolean` and `note` (what was shown, for the log).

- when: never wakes. Its trigger is **any info step for a good player**, on any night, plus
  on-death info (Ravenkeeper, Sage) and day-time verbal info.
- **Fabled reminder anchor (shared fix).** `PlacedReminder` needs a home that is not a
  seat. Cheapest option: keep the token in `FabledEntry` itself —
  `FabledEntry.used = true` renders as a `NO ABILITY` chip on the Fabled token in the
  grimoire corner (`GrimoireScreen.kt:205-219`) and in the Fabled sheet row. This also
  serves the Spirit of Ivory, Revolutionary and Storm Catcher (out of scope) and avoids
  inventing a pseudo-seat.
- **The offer, at every info step.** In `StepDetailPanel`, after the computed
  `InfoResult` (`NightScreen.kt:865-930`), when `fibbin` is active, `!used`, and the step's
  holder is a good player, render a distinct row:

  > **Fibbin available (once per game)** — give incorrect information instead?
  > [ 0 ] [ 1 ] [ 3 ] [ 4 ]   *(every value except the truth)*
  > *Information only — the Fibbin never makes an ability fail.*

  Tapping a value: shows `ShowCard.NumberCard(n)` (or the flipped `YES`/`NO`,
  `ShowCards.kt:66-68`), sets `used = true`, writes
  `note = "N2 Empath (Ana): shown 1, truth 0"`, and appends a log line. Undoable like every
  other action (`GameViewModel.update`, `GameViewModel.kt:101-109`).
  This is a small generalisation of the existing impaired-only block at
  `NightScreen.kt:904-928` — reuse the same chip rendering, change the gate from
  "is impaired" to "is impaired **or** an unspent Fibbin is available".
- **The inverse offer.** When the holder *is* impaired and an unspent Fibbin is available,
  add one more chip alongside the false-info chips:
  **`[ Use the Fibbin: show the TRUE answer ]`** — showing the true value and marking the
  Fibbin used. Directly implements "Some characters get false information due to their
  ability. The Fibbin can make this information true."
- targets/constraints: the info holder must be **good**
  (`!player.isEvil(viewModel::characterById)`, `GameState.kt:49-52`). For an evil holder,
  render the row greyed with "The Fibbin only affects good players."
- **Explicitly out of scope for the Fibbin** (state this in the run-book text and never
  offer it): the Virgin's execution trigger, the Monk's/Soldier's protection, the Slayer's
  slay, the Gambler's guess, or any other non-information effect. The app should not offer
  a Fibbin affordance on `QuickResolutions` steps (`NightScreen.kt:461-527`) at all.
- immediate effects: none on the grimoire. No tokens on seats, no impairment. Critically,
  the Fibbin must **not** mark the player as drunk/poisoned — `StatusEffects.isImpaired`
  (`StatusEffects.kt:36-46`) must stay untouched, or the Mathematician, Chambermaid and
  Vortox logic would all change.
- deferred effects: none.
- expiry: none. `used` is permanent for the game.
- information: no new computation; it consumes `InfoCalc` results
  (`InfoCalc.kt:38-80`) and substitutes the shown value.
- visibility: entirely secret. The table is told the Fibbin is in play at game start
  (per How to Run) but never when or on whom it is used.
- day-time inputs: the same offer must be reachable for verbal day info (Artist, Savant,
  Fisherman). Those characters have no day-recording surface today; when one is built
  (see the Gossip/Juggler/Savant gap the user raised), the Fibbin offer belongs on it.
- interactions:
  - **Vortox**: Townsfolk info is already false; the Fibbin's use here is the *inverse*
    offer above.
  - **Drunk / poisoned / Marionette**: same — the offer is "tell the truth this once".
  - **Ravenkeeper / Sage** (on-death info): the offer must appear on their wake step too,
    which is the wiki's own example.
  - **Mathematician**: the Fibbin is not a malfunction; a Fibbin-lied player must **not**
    be counted by the Mathematician. Add that line to the Mathematician's caveat
    (`InfoCalc.kt:77-80` currently says "Track malfunctions manually").
  - **Fabled immunity**: nothing can poison, drunk or remove the Fibbin.

**UI text:**
- Fabled sheet row, unspent: "Fibbin · available — one good player may get false info."
- Fabled sheet row, spent: "Fibbin · used on night 2 (Empath) — NO ABILITY."
- Info step row: "Fibbin available (once per game) — show a false answer instead?"
- Guard line under it: "Information only. The Fibbin never makes an ability fail."

**Data changes:**
- `characters.json:2226` — align the ability wording with the wiki
  ("Once per game, 1 good player might get incorrect information.") if the project's rule
  is wiki-first.
- `characters.json:2232` — `"reminders": ["Used"]` → `["No ability"]`, matching the wiki's
  **NO ABILITY** and the label the existing "Mark spent" code already writes
  (`NightScreen.kt:271-274`).
- `night_guide.json` — add a `fibbin` entry once the guide gains a non-night section,
  carrying the How-to-Run text and the "information only" warning verbatim.
- night order data: no change.

## Tests to add

1. `fibbin offer appears for a sober good info holder`
   Given `fabled = [fibbin]` unspent and a sober Empath with a true count of 0,
   When the Empath's night step is built,
   Then a Fibbin offer is present with candidate values {1, 2, 3, 4}.
   *(Fails today: `NightScreen.kt:904-907` gates on impairment.)*
2. `fibbin offer is absent for an evil info holder`
   Given the same state but the info holder is the evil Village Idiot,
   Then no Fibbin offer is present.
3. `using the fibbin marks it spent exactly once`
   Given an unspent Fibbin, When it is used on the Empath,
   Then `fabled["fibbin"].used` is true, the offer disappears from every subsequent info
   step, and a log entry records the step, the truth and the shown value.
4. `the fibbin does not impair the player`
   Given the Fibbin used on Ana, Then `StatusEffects.isImpaired(state, lookup, ana)` is
   false and Ana holds no new reminder.
5. `the fibbin can make impaired info true`
   Given a poisoned Empath (true count 0, app would advise lying) and an unspent Fibbin,
   Then the step offers a "show the TRUE answer" action, and taking it marks the Fibbin
   spent.
6. `fibbin state round-trips`
   Given `FabledEntry("fibbin", used = true, note = "N2 Empath: shown 1, truth 0")`,
   Then it survives serialization.
7. `fibbin adds no night step`
   Given `fabled = [fibbin]`, Then neither night order contains a `"fibbin"` step.
   *(Passes today — regression guard.)*
