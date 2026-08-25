# Buddhist (buddhist) — fabled Fabled

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Buddhist> (fetched verbatim via
`api.php?action=parse&page=Buddhist&prop=wikitext`, 2026-08-25).

Current ability text (verbatim summary line):

> "For the first 2 minutes of each day, veteran players may not talk."

**Summary bullets (verbatim):**

- "Use the Buddhist to help new players have fun when there are one or two veterans in a group of new players."
- "When experienced players find themselves in a game full of beginners, the veterans will often dominate the game due to their enthusiasm and knowledge."
- "Players affected by the Buddhist cannot talk at all for the first two minutes of each day. They may not whisper in private, and may not talk to each other. They simply listen."
- "This is not a punishment for being talkative. Being talkative is great! Blood on the Clocktower is a talking game, and the more, the merrier. That said, forcing the veterans to stay silent temporarily each day allows the new players to find their own voices, to come up with their own theories, and to take action on their own. It is about fun for everybody."
- "It is common for a player to say 'I am a Buddhist' or for the Storyteller to say to them 'You are a Buddhist.' This doesn't mean that their character is the Buddhist."

**How to Run (verbatim):**

> At the start of the game, declare that the Buddhist is in play. Add the Buddhist token to the Grimoire. Declare which players are Buddhists, with their consent. Those players must stay silent for the first two minutes of each day.
>
> Remove the Buddhist at any time, declaring when you do so.
>
> *(storyteller box)* If no device is available to set a two-minute timer, then use your best judgment as to what is two minutes, and inform the veterans when you feel that the time is up.
>
> *(storyteller box)* Like the Angel and the Revolutionary, you'll want to ask for a player's consent before affecting them with the Buddhist. Something like this is best: "Do you mind if I make you a Buddhist for this game? You are really good at the game, and I want to give the new players a chance to figure things out for themselves. Is that okay?"

**Examples (verbatim):**

1. "Lachlan and Lewis are veterans in a game of mostly new players. To encourage the new players to talk, the Storyteller puts the Buddhist in play. Lachlan and Lewis may not talk for the first two minutes each day, after which, they may talk freely."
2. "Evin is affected by the Buddhist. He is a Minion and simply listens to what people are saying for the first two minutes, allowing him to bluff as a not-in-play character later on."

**Storyteller obligations, distilled:** (a) name the affected players, with consent, at
game start; (b) **every day, at the moment the day begins**, start a two-minute clock and
say who must be silent; (c) tell them when time is up. This is the one Fabled whose entire
implementation is *a timer that fires itself at dawn* — the app already has the timer
widget and the dawn transition, and simply never connects them.

**Jinxes:** none.
**Night order:** never wakes. Correctly absent from both order lists.

## What the app does today

Data:
- `characters.json:2159-2170` — ability text matches the wiki exactly; `team: fabled`,
  `setup: false`, **no reminders**, no night reminders.
- `night_and_jinxes.json` — correctly absent from both order lists. **Works.**
- `night_guide.json` — no entry. Correct for the night sheet, but there is no day-time
  run-book schema (`NightGuideEntry` has only `first`/`other`, `NightGuide.kt:36-40`), so
  the Buddhist's How-to-Run text lives nowhere in the app.

Code — zero engine awareness. The Buddhist is a toggle and a token:
- `GameExtras.kt:167-195` `FabledSheet` — tapping toggles the id into
  `state.fabledIds` (`GameActions.kt:211-212`). Nothing asks who the veterans are.
- `GrimoireScreen.kt:215-218` — a 30dp token in the corner of the circle.
- `NightOrder.kt:144-145` — no night step (the id is not in either order list). Correct.

The nearest thing to support is the discussion timer:
- `components/Timer.kt:38-107` `DiscussionTimer` — a wall-clock-anchored countdown with
  three presets, **1m / 2m / 5m** (`Timer.kt:88`). Idle it collapses to a single icon
  button; expired it reads "Time!  ×" until tapped away (`Timer.kt:78-84`).
- `GameShell.kt:314-320` — it is rendered **only** on the Grimoire and Day tabs, floating
  bottom-end. It never starts itself.

So the storyteller's day-start routine is entirely manual: press Dawn → announce deaths →
remember that this game has a Buddhist → remember which two players it affects → switch to
the Grimoire or Day tab → tap the timer icon → tap "2m" → announce the silence → watch for
"Time!" (only visible if they happen to be on one of those two tabs) → announce time is up.
Nothing in the app carries any of that.

## Defects and gaps

1. **P1** · Who the Buddhists are is not stored anywhere. `GameState.fabledIds`
   (`GameState.kt:98`) holds only the id; `FabledSheet` (`GameExtras.kt:167-195`) asks no
   follow-up question. The names, and the fact that consent was obtained, live in the
   storyteller's head. *Repro:* Fabled… → Buddhist → nothing further is asked; no seat
   shows any Buddhist marking.
2. **P1** · The two-minute silence is never started, prompted or announced. The ability is
   "for the first 2 minutes of **each** day" — every single dawn transition
   (`GameActions.advancePhase`, `GameActions.kt:258-263`) is a missed trigger. The
   storyteller must remember it unaided, every day, in the middle of announcing deaths.
3. **P2** · No day-start briefing surface exists at all to hang it on. `DayScreen.kt:85-92`
   opens with "Day N · X alive · Y votes to execute" and nothing else; the phase advance
   (`GameShell.kt:162-167`) jumps to the Day tab with no dawn summary. This is the same
   missing surface that the Angel, Doomsayer, Ferryman and Duchess specs all need.
4. **P2** · The timer cannot be aimed at the Buddhist. It has no label, no "start on dawn"
   behaviour, and its state (`Timer.kt:41-43`, `rememberSaveable` local state) is not part
   of `GameState`, so it is invisible to undo/redo and to the log.
5. **P2** · Timer visibility is tab-dependent (`GameShell.kt:314`). If the storyteller is
   on the Night tab tidying reminders, or in the Script tab, the "Time!" state is
   invisible; there is no sound, vibration or persistent banner. On an iPhone PWA this is
   exactly the moment the phone is face-down on the table.
6. **P2** · No card to show the affected players ("You are a Buddhist this game — please
   stay silent for the first two minutes of each day"). `ShowCards.kt:65-77` has
   `Message`, which would serve, but no preset exists.
7. **P3** · Nothing records that the Buddhist was added or removed mid-game (the wiki
   explicitly allows "Remove the Buddhist at any time, declaring when you do so"); the game
   log (`GameExtras.kt:46-106`) covers deaths and nominations only.
8. **P3** · No consent affordance. Given the wiki devotes a whole storyteller box to asking
   permission, a checkbox "consent asked" next to each selected player is cheap and
   matches how the app already gates setup choices.

## Proposed behaviour (spec)

Shares the `FabledEntry` storage introduced in `angel.md`; the Buddhist uses
`playerIds` (the veterans) and `note` (optional, e.g. "Lachlan asked to opt out from day 3").

- when: never wakes at night. The Buddhist's only trigger is **the NIGHT→DAY transition**,
  every cycle, for as long as the entry exists.
- setup input: when `buddhist` is added and `playerIds.isEmpty()`, show a dialog in the
  same family as the Fortune Teller / Drunk prompts (`GameShell.kt:341-475`):

  > **Who is a Buddhist?**
  > They must stay silent for the first 2 minutes of each day. Ask their consent first:
  > "Do you mind if I make you a Buddhist for this game?"
  > [multi-select of all seats] · [ ] consent asked
  > [ Save ] [ Later ]

  Selected seats get `PlacedReminder("buddhist", "Silent")` so the grimoire shows it at a
  glance. (Requires adding `"Silent"` to `characters.json` `reminders` for `buddhist`,
  which is currently `[]`.)
- immediate effects: none mechanical. No impairment, no status effect —
  `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) must **not** treat the token as
  drunk/poisoned (its label must therefore avoid the substrings "poison"/"drunk"; `Silent`
  is safe).
- deferred effect — **the dawn trigger**. On `advancePhase` NIGHT→DAY with `buddhist`
  active and `playerIds` non-empty:
  1. start the app timer at **120 s**, anchored to wall-clock like `Timer.kt:45-50`, and
     move its state into `GameState` (`buddhistTimerEndsAt: Long`) so it survives tab
     switches, undo and process death, and so the same countdown is visible on every tab;
  2. show a persistent day-start banner (the pattern already exists for the Mastermind day,
     `GameShell.kt:513-531`):
     **"BUDDHIST — Lachlan and Lewis are silent for 1:47"**, counting down, replaced at
     zero by **"BUDDHIST — time is up, they may talk"** until dismissed;
  3. offer a one-tap `ShowCard.Message("Silence for 2 minutes", "Lachlan · Lewis")` for the
     table.
- expiry: the banner clears on dismissal or at dusk. No tokens expire —
  `("buddhist","Silent")` persists for the whole game and is removed only when the Fabled
  is removed or a player is deselected. Do **not** add it to `EXPIRES_AT_DAWN`/`DUSK`
  (`GameActions.kt:218-242`).
- information: none.
- visibility: public — the group knows who the Buddhists are. Nothing hidden from the
  Demon or Minions.
- day-time inputs: a "restart 2 min" button on the banner (for a late start), and an
  "end early" button (storyteller judgment, per the "use your best judgment" box).
- removal: removing the Buddhist from the Fabled sheet clears the `Silent` tokens and the
  banner, and writes a log line "Buddhist removed (day N)".
- interactions: none with any character. The Buddhist restricts speech only; it does not
  restrict nominating, voting, whispering-as-a-game-action, or any ability. Explicitly do
  **not** wire it into `StatusEffects.nominationWarnings` (`StatusEffects.kt:131-166`) —
  a silent player may still nominate once the two minutes are up, and a storyteller who
  wants to stop them mid-silence handles it verbally.
- generalisation: **Hell's Librarian** (out of this scope) needs the identical banner and
  penalty mechanics, and the Revolutionary needs the identical consent-and-select dialog.
  Build the "Fabled attached to players, with a consent prompt" pattern once.

**UI text:**
- Fabled sheet row when active: "Buddhist · Lachlan, Lewis · silent for 2 min each dawn".
- Day banner: "BUDDHIST — Lachlan, Lewis silent · 1:47" / "…time is up".
- Show card: title "SILENCE", subtitle "Buddhists: 2 minutes".

**Data changes:**
- `characters.json:2166` — `"reminders": []` → `"reminders": ["Silent"]`.
- `night_guide.json` — add a `buddhist` entry once the guide gains a `day` section, with
  the How-to-Run text and the consent script verbatim.
- night order data: no change.

## Tests to add

1. `buddhist stores its veterans`
   Given `setFabled` adds `buddhist` with `playerIds = [lachlan.id, lewis.id]`,
   Then those ids survive a serialization round-trip and each seat holds
   `PlacedReminder("buddhist","Silent")`. *(Fails today: no such storage.)*
2. `dawn starts the two-minute buddhist clock`
   Given phase NIGHT, cycle 2, `buddhist` active with two veterans,
   When `GameActions.advancePhase` runs,
   Then `state.buddhistTimerEndsAt == Time.epochMillis() + 120_000` (within tolerance) and
   the day-start briefing contains a Buddhist line naming both players.
   *(Fails today: `advancePhase`, `GameActions.kt:258-263`, only flips phase and clears
   dawn tokens.)*
3. `dawn does nothing when no veterans are selected`
   Given `buddhist` active but `playerIds` empty, When dawn breaks,
   Then no timer starts and the briefing instead says "Buddhist is in play but no players
   are selected."
4. `the silent token is not an impairment`
   Given `PlacedReminder("buddhist","Silent")` on Lachlan,
   Then `StatusEffects.isImpaired(state, lookup, lachlan)` is false.
   *(Guards against a future label containing "drunk"/"poison", `StatusEffects.kt:38-42`.)*
5. `removing the buddhist clears its tokens`
   Given two seats holding `("buddhist","Silent")`, When the Fabled is removed,
   Then no seat holds that token and the log records the removal.
6. `buddhist adds no night step`
   Given `fabled = [buddhist]`, Then neither night order contains a `"buddhist"` step.
   *(Passes today — regression guard.)*
