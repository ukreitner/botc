# Night action audit — 2026-10-15

The reported first-night problems exposed gaps between the engine's rules, the
controls that collect a storyteller's answer, and the card actually shown to a
player. This pass follows those paths through selection, confirmation,
presentation, saved state, and the next night. It is not an exhaustive audit of
every character or interaction.

## Confirmed issues and changes

| Area | Confirmed problem | Change |
|---|---|---|
| Lunatic | Instructions named fake Minions, but the night row offered only bluffs. | Preserve the Lunatic's fake-Minion card alongside their bluff card. The player sees names and seats without real roles or an illusion warning. |
| Grandmother | The handout showed a generic character token without the relationship or player. | Show **THIS IS YOUR GRANDCHILD**, the character token, and the Grandchild's name and seat. |
| Dreamer | The calculated answer contained one character, so the handout showed one token despite instructions requiring two. | Offer complete good/evil character pairs and display both tokens together. Custom editing preserves both selections. |
| Philosopher and optional abilities | Empty character selections could arm confirmation; contradictory pass payloads could retain an earlier character in the ledger. Incomplete player selections could spend an ability. | Require an explicit valid choice or pass, discard residual selections on pass, and reject malformed or incomplete input before effects or completion. Pass/skip/save/next-night regressions cover the optional roles. |
| Courtier | The real character-only picker did not supply the player IDs expected by the effect, so the ability could spend without making its target drunk. | Resolve the named character to its holder; if duplicated, ask which copy. Out-of-play use still spends. A use made while poisoned leaves no dormant effect that activates later. |
| Impaired Philosopher | A failed gain could become a functioning borrowed ability after recovery. | Keep the believed ability permanently ineffective while retaining its simulated wakes and spending. A previously successful gain still resumes after temporary impairment. |
| Vortox | Drunk or poisoned Townsfolk were allowed true information despite an active Vortox. | Vortox false-information requirements take precedence over recipient impairment; an impaired Vortox does not impose them. |
| Godfather | Zero Outsiders produced an empty token list and no truthful handout. | Offer an explicit no-Outsiders card. Multiple Outsiders remain a combined token card. |
| Nightwatchman | The source row showed the token to the Nightwatchman, while the target was logged as told without a real handout. Poison could still produce a target wake record. | Queue a required, separately addressed recipient card. Under Vortox, select a false player before creating that card. Passing does not spend; an impaired use spends but produces no recipient handout. A Goon reaction also prevents the handout. |
| Preacher | The source received its own reveal; the chosen Minion had no required handout. Restricting choices to Minions discarded non-Minion choices and Goon reactions. | Permit every legal player, record the actual choice, then apply suppression and queue the Minion's handout when appropriate. Preserve the Marionette exception and handle dead recipients. Poisoned use creates neither a new suppression effect nor a handout. |
| Shared hold control | A retained callback could act on an earlier target after the selection changed; cancelled gestures needed an explicit no-action guarantee. | Confirmation reads the current callback. Early release/cancellation cannot commit; a completed hold commits once. Saved gate/override state is scoped to the correct player and night. |
| Typed action rendering | Gambler, Engineer, Al-Hadikhia, and General offered an enabled completion button without controls for their required structured answers. Some advanced empty; Engineer silently did nothing. | Render supported sequences, independent option answers, and player/character assignments. Require complete input, reset dependent answers when a target changes, and preview only the selected consequences. |
| Engineer assignments | The broad action contract allowed mixed Demon/Minion changes and moving an existing character to another player. | Normal choices preserve character type and existing owners, and rebuild the Minions or one Demon. Invalid assignments show a reason and cannot confirm; an explicit storyteller override remains available. |

The original Philosopher screenshot's exact accidental-spend trigger was **not**
reproduced by an explicit pass on the baseline APK: that pass correctly left the
ability available. The changes address separately confirmed input and state
defects; they do not establish what happened in that particular saved game.

## Rules used

- [Lunatic](https://wiki.bloodontheclocktower.com/Lunatic): Demon-style fake Minions and three good-character bluffs, which may be in play.
- [Grandmother](https://wiki.bloodontheclocktower.com/Grandmother): show the Grandchild's character and identify the player. The relationship wording is the requested app presentation.
- [Dreamer](https://wiki.bloodontheclocktower.com/Dreamer): one Townsfolk/Outsider and one Minion/Demon, classified by character type rather than current alignment. The actual character remains relevant after gaining another ability; self and Traveller targets are excluded.
- [Philosopher](https://wiki.bloodontheclocktower.com/Philosopher), [Courtier](https://wiki.bloodontheclocktower.com/Courtier), and [Seamstress](https://wiki.bloodontheclocktower.com/Seamstress): distinguish declining from using the once-per-game ability.
- [Vortox](https://wiki.bloodontheclocktower.com/Vortox): Townsfolk information must be false even when its recipient is drunk or poisoned.
- [Nightwatchman](https://wiki.bloodontheclocktower.com/Nightwatchman): chooser and recipient wake separately; drunk use does not wake the target, and the Vortox changes the player identified.
- [Preacher](https://wiki.bloodontheclocktower.com/Preacher) and [Marionette](https://wiki.bloodontheclocktower.com/Marionette): a chosen Minion learns about selection, except the Marionette must not be exposed by that wake.
- [Godfather](https://wiki.bloodontheclocktower.com/Godfather) and [Poppy Grower](https://wiki.bloodontheclocktower.com/Poppy_Grower): distinguish a complete information handout from instructions merely describing one.
- [Engineer](https://wiki.bloodontheclocktower.com/Engineer): choose Minions or Demon, preserve their types and existing character owners, and handle unusual counts through an explicit storyteller ruling.

Dead Minions remain included because the [Glossary](https://wiki.bloodontheclocktower.com/Glossary)
defines in-play characters as alive or dead. The Engineer's ordinary picker
requires assignments for all current Minions, including unchanged ones, with
distinct destination characters. This is an app guard for the usual case, not
a complete ruling for altered Minion counts, duplicate-character scripts, or
jinxes. The Engineer wiki specifies the setup-based Minion count and storyteller
adjudication; it does not separately state a duplicate-selection rule. Use the
explicit storyteller override for such adjudications. Existing-owner retention
is explicit in the rule.

## Validation evidence

- [Night handouts emulator report](2026-10-15-night-cards-emulator.md): baseline and updated UI evidence for the four reported characters, poisoned Dreamer, latest-target/cancelled-hold behavior, and the Nightwatchman recipient. Includes reproducible synthetic fixtures and screenshot paths.
- [Complex night actions emulator report](2026-10-15-complex-night-emulator.md): reproduced missing inputs for Gambler, Engineer, Al-Hadikhia, and General; distinguishes its intermediate baseline from the later renderer build.
- Engine regressions: `InformationHandoutRegressionTest`, `OptionalAbilityInteractionTest`, `NightwatchmanCommunicationTest`, and `PreacherCommunicationTest`.
- UI-model regressions: `InformationHandoutPresentationTest`, `NightControlStateTest`, `TypedNightInputTest`, and existing night-card/row suites.

Integrated checks on the final source:

- Full engine suite: **1,009 passed**, no failures or skipped tests.
- Full Compose UI typecheck and model suite: **213 passed**, no failures or skipped tests.
- Android `:app:assembleDebug`: **passed**.
- Web `wasmJsBrowserDistribution`: **passed** (existing deprecation and bundle-size warnings).
- `git diff --check`: **passed**.
- Emulator APK SHA-256: `85c74a6f93e444e3db3312af0cdbb5f8569c9dd0b14373404f7762e39f33bc61`.

Both emulator runs are complete. The integrated APK passed standard/custom
Nightwatchman handout completion without replay, pending-recipient wording,
Gambler's correct/wrong/reset paths, Engineer assignments/rejection/decline,
Al-Hadikhia ordered death/resurrection and no-one reset, and General selection
persistence. Both crash buffers were empty. The reports distinguish the earlier
card-build checks from the integrated-build checks and include UI-tree,
screenshot, and persisted-state evidence. Both disposable emulators were stopped.

This local debug APK is a test artifact, not the signing-compatible release used
by the in-app updater. No remote branch or release has been changed by this pass.

## Remaining boundaries

- **Death-choice sheet can discard the rest of a night action (pre-existing):**
  when a selected attack needs a storyteller ruling, such as a healthy Mayor's
  night death, [NightCard.kt](../../app/src/main/java/com/clocktower/grimoire/ui/screens/night/NightCard.kt)
  opens the death sheet for the first such target instead of submitting the
  full night input. Its [NightScreen.kt completion callback](../../app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt#L308)
  records that one target and marks the entire step done; it does not resume
  `NightPlan.resolve` with the remaining choices. For example, Al-Hadikhia
  choosing a Mayor and two other players can resolve the Mayor ruling but omit
  the other selected deaths or resurrections. Shabaloth/charged-Po multi-target
  attacks and other actions with additional effects can lose their remaining
  effects through the same route. This path predates these changes and was
  confirmed by source inspection, not a final-APK reproduction. Such cases
  still require the storyteller to finish and verify every remaining effect
  manually; a ticked row is not evidence that the complete action ran.
- **Poppy Grower grouped reveal:** the death row currently describes the reveal
  but lacks a complete recipient-aware handout sequence. Adding cards directly
  to that row would address them to the dead Poppy Grower. Follow-up must wake
  the Minions together, then the Demon, while preserving Marionette omissions,
  Magician information, and correct presentation records.
- **Unsupported nested or conflicting sequences:** the typed renderer supports
  the answer shapes it can represent without overwriting another stage. Nested
  sequences and combinations needing separate target/character answers remain
  blocked from ordinary confirmation and explicitly direct the storyteller to
  run the action manually, then use Skip. Manual handling is not automatic rule
  resolution or proof that the complete action was recorded.
- **Interaction coverage:** these fixtures cover selected healthy, impaired,
  false-information, pass, restart, and recipient cases. They do not exhaust all
  jinxes, gained abilities, duplicate characters, scripts, or day/night effects.
