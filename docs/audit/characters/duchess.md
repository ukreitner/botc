# Duchess (duchess) — fabled Fabled

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Duchess> (fetched verbatim via
`api.php?action=parse&page=Duchess&prop=wikitext`, 2026-08-25).

Current ability text (verbatim summary line):

> "Each day, 3 players may choose to visit you. At night\*, each visitor learns how many visitors are evil, but 1 gets false info."

**Summary bullets (verbatim):**

- "Add the Duchess if your script has too little information or too much misinformation."
- "Each player that visits the Duchess learns how many visitors are evil, **including themself**. However, one visitor of the Storyteller's choice will get false information."
- "Players that visit the Duchess still get to use their ability normally. The Duchess does not make their ability give false information."
- "The players decide amongst themselves which players will be the three players to visit. **If exactly three visitors cannot be decided upon, then the Duchess does not act tonight.**"

**How to Run (verbatim — this is the passage the app's data contradicts):**

> At the start of the game, declare that the Duchess is in play. Add the Duchess token and their reminders to the Grimoire. Each day, any player may volunteer to visit the Duchess tonight.
>
> If exactly three players volunteer to visit the Duchess tonight, then mark **two** of them with a **VISITOR** reminder and **one** of them with a **FALSE INFO** reminder. If more or less than three players volunteer to visit, do not add these reminders.
>
> At night, wake each player marked **VISITOR** or **FALSE INFO** one at a time. Show the woken player the Duchess token. If the woken player is marked VISITOR, show them fingers (*0, 1, 2, or 3*) equaling the number of evil players who are marked **either VISITOR or FALSE INFO**. If the woken player is marked **FALSE INFO**, show them any number of fingers except the correct number. Put the woken player to sleep.
>
> Remove the Duchess at any time, declaring when you do so.

**Examples (verbatim) — they pin down the count exactly:**

1. "The Soldier, Pacifist, and Sage visit the Duchess. The Soldier and Pacifist learn a '0.' The Sage learns a '1.'"
   → 0 evil among the three; the two VISITORs get the true **0**; the FALSE INFO player gets 1.
2. "The Mutant, Butler, and Po visit the Duchess. The Mutant learns a '1,' the Butler learns a '2,' and the Po learns a '1.'"
   → 1 evil (the Po); true answer 1; the Butler is the FALSE INFO player.
3. "The Mastermind, Imp, and Minstrel visit the Duchess. The Mastermind learns a '2,' the Imp learns a '1,' and the Minstrel learns a '2.'"
   → 2 evil (Mastermind + Imp); true answer 2; **the Imp — himself one of the two evil visitors — is the FALSE INFO player**, and he is still counted in everyone else's number.

**Rules distilled:**

| | |
|---|---|
| Timing | Visits are volunteered **during the day**; the wake happens on **other nights only** ("At night\*"). No first-night action. |
| Trigger | **Exactly three** volunteers. Fewer or more → place no tokens, and the Duchess does not act that night. |
| Tokens | **2 × VISITOR + 1 × FALSE INFO**, on the three volunteers. |
| The number | The count of **evil players among all three marked players**, including the FALSE INFO one and including the woken player themself. Range **0–3**. |
| False info | The FALSE INFO player gets **any number except the true one** (so 0–3 minus the truth). |
| Not an ability | Visitors keep and use their own abilities normally; the Duchess does not drunk/poison them. |
| Fabled immunity | "The Fabled cannot die or lose their ability" (<https://wiki.bloodontheclocktower.com/Fabled>) — so poisoning a *visitor* does not change what the Duchess shows, and the Vortox's "all Townsfolk abilities give false info" does not apply, because this is not a Townsfolk ability. *(Not stated explicitly on the Duchess page; flagged as an inference from the Fabled page, worth confirming with a rules source before hard-coding.)* |
| Misregistration | Not addressed by the wiki. Spy-registers-good / Recluse-registers-evil is a storyteller call here; the app should surface it, not decide it. |

**Jinxes:** none.
**Night order:** `duchess` is the **only** Fabled in this scope that appears in a night
order list, and only on other nights.

## What the app does today

Data:
- `characters.json:2195-2209` — ability text matches the wiki exactly. `team: fabled`,
  `setup: false`, `reminders: ["Visitor", "False Info"]`, and
  `otherNightReminder`:

  > "Wake each player marked \"Visitor\" or \"False Info\" one at a time. Show them the Duchess token, then fingers **(1, 2, 3)** equaling the number of evil players marked **\"Visitor\"** or, if you are waking the player marked \"False Info,\" show them any number of fingers except the number of evil players marked \"Visitor.\""

  Two rule errors, both inherited verbatim from the community dataset
  (`bra1n/townsquare` `src/fabled.json`): **(a)** the finger range omits **0**, which
  wiki example 1 shows is the true answer for an all-good trio; **(b)** it counts only
  players marked `"Visitor"`, excluding the `"False Info"` player, which wiki example 3
  shows is wrong — the Imp is FALSE INFO and is still counted in the Mastermind's and
  Minstrel's "2".
- `night_and_jinxes.json:452` — `duchess` sits in `otherNight` between `towncrier` and
  `oracle` (…`flowergirl`, `towncrier`, **`duchess`**, `oracle`, `seamstress`…). Correctly
  absent from `firstNight`. *(I could not retrieve the official night sheet to confirm the
  exact slot — `wiki.bloodontheclocktower.com/Night_Order` 404s and the script tool's data
  endpoints are not public. Flagged as unverified, P3.)*
- `night_guide.json:175-186` — the only Fabled with a guide entry:

  > "Only act if players visited the Duchess today. Wake each player marked Visitor or False Info one at a time: show them the Duchess token, then a finger signal for the number of evil players **among the marked visitors** - except for the player marked False Info, who must be shown any number other than the true one. Put each player back to sleep before waking the next, then clear the Visitor and False Info reminders."

  This text is **correct** ("among the marked visitors") — and it therefore **contradicts
  the `characters.json` text**, and both are shown in the same expanded step
  (`NightScreen.kt:792-800` renders the guide instructions whenever they differ from
  `step.detail`, which is the `characters.json` reminder — `NightOrder.kt:146-148`).
  One entry has a show card: `{label: "Show Duchess token", kind: "token", token: "self",
  text: "This many of the Duchess's visitors are evil (see my fingers)"}`.

Code:
- `NightOrder.build` (`NightOrder.kt:140-178`) — `isFabledActive = fabled.contains(id)`
  (`:144`) and `if (holders.isEmpty() && !isFabledActive) continue` (`:145`). So with
  `duchess` in `fabledIds`, a step appears on **every** other night, titled "Duchess",
  with `playerIds = emptyList()` and detail = the (wrong) `otherNightReminder`.
- `NightScreen.QuickResolutions` (`NightScreen.kt:461-467`) opens with
  `val holder = step.playerIds.firstOrNull()?.let { state.player(it) } ?: return` —
  a Fabled step has no holder, so **no quick resolution is ever offered**.
- `InfoCalc.supports` (`InfoCalc.kt:30-36`) does **not** list `duchess`, so
  `StepDetailPanel`'s whole info block (`NightScreen.kt:836-930`) — the computed number,
  the impairment caveats, the "Show N full-screen" chip and the "False info to show
  instead" chips — is skipped entirely.
- `NightToolTray` (`NightScreen.kt:193-352`) — expanding the Duchess step does give the
  storyteller the `Visitor` / `False Info` chips (`character.allReminders`,
  `Character.kt:62`). Placement goes through `NightScreen.kt:317-336`:
  ```kotlin
  val availableCopies = character.allReminders.count { it == pendingReminderLabel }
  if (availableCopies <= 1) GameActions.placeExclusiveReminder(...)
  ```
  `"Visitor"` appears **once** in `characters.json:2204-2207`, so `availableCopies == 1`
  and every placement is *exclusive* — `placeExclusiveReminder` (`GameActions.kt:194-201`)
  strips the token from every other seat first. **Marking a second visitor silently
  un-marks the first.**
- `SeatSheet.ReminderPicker` (`SeatSheet.kt:492-500`) sources tokens from
  `gameData.resolve(state.script)`; built-in scripts exclude Fabled
  (`GameData.kt:39-42`), so during the **day** — when visits are actually declared — the
  Duchess tokens are unreachable from a seat. Only the generic chips
  (`SeatSheet.kt:502`: Drunk, Poisoned, Dead, Protected, Mad, Good, Evil, Used, ?) are
  available.
- `GameActions.EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`) — no
  `duchess` rows. The tokens persist until removed by hand, exactly as the night guide's
  last sentence instructs.

Storyteller's experience: during the day, three players say they'll visit; there is nowhere
to record that, so they use generic `"?"` chips or remember. At night, the Duchess step
appears whether or not anyone visited. They open it, read two contradicting instructions,
tap `Visitor` → seat A, then `Visitor` → seat B, and seat A silently loses its token. They
then count the evil visitors themselves, in their head, off the grimoire. They show the
Duchess token via the guide card, then hold up fingers by hand — there is no number card,
because `InfoCalc` doesn't support the Duchess. Then they remove three tokens by hand.

## Defects and gaps

1. **P0** · The night instruction in `characters.json:2202` states the wrong count. It says
   to show "the number of evil players marked **'Visitor'**", excluding the FALSE INFO
   player. Wiki: "equaling the number of evil players who are marked **either VISITOR or
   FALSE INFO**", confirmed by example 3 (the evil Imp is the FALSE INFO player and is
   counted in the other two players' "2"). A storyteller who follows the app's text gives
   the wrong number to *every* visitor in any game where the FALSE INFO player is evil.
   *Repro:* Fabled… → Duchess → Dusk → Night tab → the "Duchess" row's detail text.
2. **P0** · The same string omits **0** from the finger range ("fingers (1, 2, 3)"), so an
   all-good trio has no legal answer. Wiki: "(*0, 1, 2, or 3*)", and example 1 shows two
   players learning "0".
3. **P0** · Only one `Visitor` token exists, so the two visitors cannot both be marked.
   `characters.json:2204-2207` lists `"Visitor"` once; `NightScreen.kt:319-326` therefore
   takes the `availableCopies <= 1` branch and calls
   `GameActions.placeExclusiveReminder` (`GameActions.kt:194-201`), which clears the token
   from all other seats. *Repro:* expand the Duchess night step → tap `Visitor` → tap
   Ana → tap `Visitor` → tap Ben → Ana's token is gone. Silent; no warning.
4. **P0** · `characters.json` and `night_guide.json` give **contradictory instructions in
   the same expanded step**. `NightScreen.kt:792-800` prints the guide text only *because*
   it differs from the reminder text; the storyteller reads "evil players marked Visitor"
   immediately above "evil players among the marked visitors" and has to adjudicate between
   their own app's two answers, at night, at the table.
5. **P1** · The visit is a **day-time input with no day-time surface**. The wiki's flow is:
   day → players volunteer → storyteller marks 2+1 → night → wake them. The app can only
   place Duchess tokens from the Night tab's tool tray (`NightScreen.kt:202`, keyed to the
   expanded step's character) because `ReminderPicker` excludes Fabled on built-in scripts
   (`SeatSheet.kt:498-500`, `GameData.kt:39-42`). So the storyteller cannot record the
   visit when it is announced.
6. **P1** · No count is computed. The Duchess is a pure counting ability over three marked
   seats — the app already computes far harder numbers (`InfoCalc.chef`, `empath`,
   `clockmaker`, …) with impairment and misregistration caveats, and does not support this
   one (`InfoCalc.kt:30-36`). The storyteller counts evil visitors by eye, per visitor,
   three times a night.
7. **P1** · No number card. Because `InfoCalc` returns null, `StepDetailPanel` never renders
   the "Show N full-screen" chip (`NightScreen.kt:889-895`) or the false-info chips
   (`NightScreen.kt:904-928`). The show-card infrastructure has `NumberCard`
   (`ShowCards.kt:68`) and it is unreachable from this step; the storyteller must open the
   generic "Show a card…" tool and pick a number by hand, three times, or hold up fingers.
8. **P1** · The tokens never expire. `EXPIRES_AT_DAWN` (`GameActions.kt:218-225`) has no
   `duchess` rows, so `Visitor` / `False Info` survive into the next day and the next
   night; the night guide's own last sentence tells the storyteller to clear them by hand.
   Left in place, tomorrow's step silently reuses yesterday's visitors.
9. **P1** · The step appears every other night regardless of whether anyone visited
   (`NightOrder.kt:144-145`), with the same wording, and must be checked off to pass the
   dawn guard (`GameShell.kt:147-160` blocks the Dawn button while any step is unchecked).
   The rule is "If exactly three visitors cannot be decided upon, then the Duchess does not
   act tonight" — the step should either be absent or explicitly self-describe as "no
   visitors tonight — skip".
10. **P2** · The step row shows no player names (`NightStepRow`, `NightScreen.kt:701` uses
    `step.playerIds`, empty for Fabled), so the one thing the storyteller needs at a glance —
    *who is visiting tonight* — is not on the row.
11. **P2** · Misregistration is unhandled and unmentioned. If the Recluse or Spy is one of
    the three, the count is a judgement call; `InfoCalc` already produces exactly this kind
    of caveat for other roles and there is no equivalent here.
12. **P2** · No "wake them one at a time / put each back to sleep" scaffolding. The guide
    says it in prose; the step offers no per-visitor sub-checklist, so on a three-visitor
    night the storyteller tracks which of the three they have already woken.
13. **P3** · Night-order position between `towncrier` and `oracle`
    (`night_and_jinxes.json:452`) is unverified against the official night sheet.

## Proposed behaviour (spec)

Shares the `FabledEntry` storage introduced in `angel.md`; the Duchess stores tonight's
visitors on the seats (as tokens) and needs no extra fields.

- **when:** other nights only (`otherNight`), never first night. Wake condition:
  exactly three seats hold Duchess tokens (2 × `Visitor` + 1 × `False Info`). If not, emit
  a step that reads **"No visitors tonight — skip"** and mark it auto-done (or omit it
  entirely; auto-done is friendlier because the storyteller can still see the Duchess is in
  play). Change `NightOrder.kt:144-145` so a Fabled step can declare itself inactive.
- **day-time input (the missing half):** a **Duchess card on the Day tab**, above
  "New nomination" (`DayScreen.kt:126`), shown whenever `duchess` is active:

  > **Duchess — who is visiting tonight?**
  > Pick exactly 3. One of them gets false info.
  > [chips: every seat] · selected 2/3
  > Then: **False info goes to:** [chips: the 3 selected]
  > [ Confirm visits ] [ Nobody agreed — no visit tonight ]

  Confirm places `("duchess","Visitor")` on the two and `("duchess","False Info")` on the
  third, and logs "D<n>: Ana, Ben, Cara visit the Duchess (Cara gets false info)".
  The same card must also be reachable at night (the tool tray) for late corrections.
- **targets:** exactly 3 seats, any alignment, alive or dead — the wiki says "any player
  may volunteer" and does not restrict to the living. *(Dead players volunteering is not
  addressed; default the picker to alive players, allow dead with a note.)* Default sort:
  seating order. The picker must refuse to confirm at any count ≠ 3, matching
  "If more or less than three players volunteer to visit, do not add these reminders."
- **tokens:** `characters.json` `reminders` becomes
  `["Visitor", "Visitor", "False Info"]` so `availableCopies == 2` for `Visitor` and the
  multi-copy branch at `NightScreen.kt:327-335` takes over (place until 2 are out, then
  recycle the oldest). Alternatively make Fabled tokens exempt from the exclusive path
  entirely; the data fix is smaller and self-documenting.
- **immediate effects:** none. No poison, no drunk, no protection, no death. Explicitly:
  visiting must **not** register as impairment —
  `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) matches labels containing
  "poison"/"drunk", and `Visitor`/`False Info` are safe, but a future rename must not
  break that.
- **information (the core):** add `duchess` to `InfoCalc.supports`
  (`InfoCalc.kt:30-36`) with `targetsNeeded = 0` (targets come from the tokens, not chips):

  ```
  visitors = players holding ("duchess","Visitor") or ("duchess","False Info")
  trueCount = visitors.count { it.isEvil(lookup) }        // 0..3, includes False Info player
  headline  = "$trueCount of the 3 Duchess visitors are evil"
  detail    = "Visitors: Ana, Ben, Cara (false info: Cara)"
  caveats:
    - if visitors.size != 3   -> "Only N players are marked — the Duchess does not act tonight."
    - if any visitor is a Recluse -> "Recluse may register as evil — your call."
    - if any visitor is a Spy     -> "Spy may register as good — your call."
    - if any visitor is alignment-flipped -> name them and the flip
    - "Visitors' own abilities are unaffected. A poisoned visitor still gets the true number."
  ```
  The impairment caveats that other roles get (`InfoCalc` Vortox/poison handling) must be
  **suppressed** here: the Duchess is a Fabled and cannot lose its ability, and the Vortox
  affects Townsfolk abilities, not this one. This is the one place the generic
  `impaired` detection at `NightScreen.kt:904-907` must not fire.
- **show cards:** the step offers, per visitor, in wake order:
  `» Ana (Visitor) — show 2`, `» Ben (Visitor) — show 2`,
  `» Cara (FALSE INFO) — show 0 / 1 / 3` (every number except the truth, 0–3).
  Each opens `ShowCard.CharacterCard("This many of the Duchess's visitors are evil", "duchess")`
  followed by `ShowCard.NumberCard(n)` — or a single combined card showing the Duchess
  token above the number, which is what actually happens at the table. Tapping a visitor's
  chip ticks them off a three-item sub-checklist so the storyteller knows who is left.
- **expiry:** add to `EXPIRES_AT_DAWN` (`GameActions.kt:218-225`):
  `"duchess" to "Visitor"`, `"duchess" to "False Info"`. The visit is for one night only;
  the next day's volunteers are marked afresh.
- **visibility:** who visits is public (the players negotiate it out loud). Which visitor
  is the FALSE INFO one is secret — never render it on a show card, only in the
  storyteller-facing panel. The Demon and Minions learn nothing special.
- **interactions:**
  - **Drunk / poisoned visitor:** still gets the true number (Fabled immunity). State this
    in the panel so the storyteller doesn't second-guess it.
  - **Vortox:** does not invert the Duchess's number.
  - **Spy / Recluse:** offered as caveats, decided by the storyteller.
  - **A visitor who dies at night before the Duchess step:** the wiki does not address it.
    Suggest keeping them in the count and still waking them if alive at the time of the
    wake; surface a caveat "Ben died earlier tonight" rather than silently deciding.
  - **The Duchess's own night position** relative to Demon kills therefore matters; keep it
    in the info block after the kills, as the current data does.
- **removal:** removing the Duchess mid-game clears its tokens and logs the removal
  ("Remove the Duchess at any time, declaring when you do so").

**UI text for the step:**
- Row title when active: "Duchess — Ana, Ben, Cara (Cara: false info)".
- Row title when inactive: "Duchess — no visitors tonight".
- Panel first line: "Wake each visitor one at a time. Show the Duchess token, then the
  number. The false-info visitor gets any number except the true one."

**Data changes:**
- `characters.json:2202` — replace `otherNightReminder` with the wiki text:
  `"Wake each player marked \"Visitor\" or \"False Info\" one at a time. Show them the Duchess token, then fingers (0, 1, 2, or 3) equaling the number of evil players marked either \"Visitor\" or \"False Info\". If the woken player is marked \"False Info\", show them any number of fingers except the correct number."`
- `characters.json:2204-2207` — `reminders` → `["Visitor", "Visitor", "False Info"]`.
- `night_guide.json:175-186` — keep the (correct) instructions, add the 0–3 range
  explicitly, and add per-visitor show entries; drop the "clear the reminders" sentence
  once dawn expiry does it.
- `night_and_jinxes.json:452` — verify the slot against the official night sheet.

## Tests to add

1. `duchess count includes the false-info visitor`
   Given `("duchess","Visitor")` on the Mastermind and the Minstrel and
   `("duchess","False Info")` on the Imp,
   When `InfoCalc.compute(data, state, "duchess", null)` runs,
   Then the headline number is **2**. *(Fails today: `InfoCalc.supports("duchess")` is
   false, `InfoCalc.kt:30-36`.)*
2. `duchess count can be zero`
   Given three good visitors, Then the headline number is **0** and the false-info options
   offered are exactly {1, 2, 3}.
3. `false-info options exclude the truth`
   Given a true count of 2, Then the offered false numbers are exactly {0, 1, 3}.
4. `two visitor tokens can coexist`
   Given the Duchess reminder catalogue, When `Visitor` is placed on Ana and then on Ben,
   Then both seats hold it. *(Fails today: one copy in data →
   `placeExclusiveReminder`, `GameActions.kt:194-201`.)*
5. `duchess tokens expire at dawn`
   Given all three tokens placed during NIGHT 3,
   When `GameActions.advancePhase` runs (NIGHT→DAY),
   Then no seat holds `("duchess","Visitor")` or `("duchess","False Info")`.
   *(Fails today: absent from `EXPIRES_AT_DAWN`.)*
6. `duchess step is inactive without exactly three visitors`
   Given `fabled = [duchess]` and 0 (or 2, or 4) marked seats,
   Then the other-night step for `duchess` reports inactive/"no visitors tonight".
   *(Fails today: `NightOrder.kt:144-145` emits the same active step regardless.)*
7. `duchess never appears on the first night`
   Given `fabled = [duchess]`, Then `firstNight(state, lookup)` contains no `duchess` step.
   *(Passes today — regression guard.)*
8. `a poisoned visitor still gets the true number`
   Given a visitor holding `("poisoner","Poisoned")`,
   Then the computed count is unchanged and the caveats contain no "POISONED — give false
   info" instruction, but do contain the explanatory line.
9. `misregistration is surfaced, not applied`
   Given the Recluse among the visitors, Then the count treats them by their true team and
   a caveat names the Recluse.
10. `characters.json duchess text matches the wiki`
    A data test asserting `otherNightReminder` contains `"0, 1, 2, or 3"` and
    `"either \"Visitor\" or \"False Info\""`. *(Fails today.)*
