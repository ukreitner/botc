# Audit fleet status (lead's tracking file)

Phase 1 = research (read-only, writes docs/audit/**). Phase 2 = synthesis/plan. Phase 3 = implementation waves.

## Phase 1 launched (wave 1, 20 agents)
tb-A(chef empath fortuneteller investigator librarian mayor monk) · tb-B(ravenkeeper slayer soldier undertaker virgin washerwoman) · tb-evil(imp baron poisoner scarletwoman spy butler drunk recluse saint)
bmr-A(chambermaid courtier exorcist fool gambler gossip grandmother) · bmr-B(innkeeper minstrel pacifist professor sailor tealady) · bmr-out/min(goon lunatic moonchild tinker assassin devilsadvocate godfather mastermind) · bmr-demons(po pukka shabaloth zombuul)
sv-A(artist clockmaker dreamer flowergirl juggler mathematician oracle) · sv-B(philosopher sage savant seamstress snakecharmer towncrier) · sv-out/min(barber klutz mutant sweetheart cerenovus eviltwin pithag witch) · sv-demons(fanggu nodashii vigormortis vortox)
exp-tf-A(acrobat alchemist alsaahir amnesiac atheist balloonist banshee bountyhunter) · exp-tf-B(cannibal choirboy cultleader engineer farmer fisherman general highpriestess) · exp-tf-C(huntsman king knight lycanthrope magician nightwatchman noble) · exp-tf-D(pixie poppygrower preacher princess shugenja steward villageidiot)
exp-out-A(damsel golem hatter heretic hermit ogre) · exp-out-B(plaguedoctor politician puzzlemaster snitch zealot) · exp-min-A(boffin boomdandy fearmonger goblin harpy marionette mezepheles) · exp-min-B(organgrinder psychopath summoner vizier widow wizard xaan) · exp-demons-A(alhadikhia kazali legion leviathan lilmonsta)

## Phase 1 pending (wave 2 — launch as slots free; 20 concurrent max)
- [x] exp-demons-B (launched)(lleech lordoftyphon ojo riot yaggababble)
- [x] travellers-A (launched)(apprentice bishop judge matron voudon beggar bureaucrat gunslinger scapegoat thief)
- [x] travellers-B (launched)(barista bonecollector butcher deviant harlot gangster gnome)
- [x] fabled-A (launched)(angel bootlegger buddhist djinn doomsayer duchess ferryman fibbin fiddler)
- [x] fabled-B (launched)(gardener hellslibrarian revolutionary sentinel spiritofivory stormcatcher toymaker deusexfiasco)
- [x] mechanics/night-engine.md (launched)
- [x] mechanics/status-model.md (launched)
- [x] mechanics/day-engine.md (launched)
- [x] mechanics/setup-and-identity.md (launched)
- [x] mechanics/records-and-memory.md (launched)
- [x] mechanics/data-accuracy.md (launched)
- [x] ux/night-screen.md (launched)
- [x] ux/grimoire-and-seats.md (launched)
- [x] ux/day-screen.md (launched)
- [x] ux/setup-and-home.md (launched)
- [x] ux/friction-log.md (launched)

## Build commands (verified working locally 2026-08-25)
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :engine:test            # engine unit tests
./gradlew -p tools/uicheck compileKotlin   # Compose UI typecheck (no Android SDK)
./gradlew -p web wasmJsBrowserDistribution # PWA build (what the user runs on iPhone)
sv-demons DONE (fanggu nodashii vigormortis vortox)
exp-out-B DONE
tb-A DONE
tb-B DONE
bmr-demons DONE

exp-tf-D DONE · exp-tf-C DONE · bmr-A DONE · exp-out-A DONE · sv-B DONE · tb-evil DONE · exp-min-A DONE · bmr-B DONE · exp-min-B DONE
NOTE: the unknown agents are CHILDREN spawned by mechanics auditors (confirmed). ux/night-screen has NOT been launched yet — launch it when a slot frees.
bmr-out/min DONE · exp-demons-A DONE
sv-A DONE · exp-tf-B DONE · sv-out/min DONE
exp-tf-A DONE
ALL 36 AUDITORS LAUNCHED. Remaining running: exp-demons-B, trav-A, trav-B, fabled-A, fabled-B, 6 mechanics, 5 ux.
records-and-memory DONE (1149 lines; unified LedgerEntry proposal + reconciliation table for the 6 parallel type names; prerequisites: PlacedReminder payload fields, Script out of GameState, shared VM interface for web)
day-engine DONE (1205 lines; execute()/checkNomination funnels, executions + dayLog lists, DayRules object, WinCheck.duskCheck, DayBriefing slots; 50 tests; two VM wrappers gotcha)
fabled-B DONE (fabledConfig payload, grimoire-level reminders, Fabled reachable in ReminderPicker, setup-stage Fabled, validateBag callers missing fabledIds)
travellers-B DONE (traveller alignment never asked; day_guide.json; ExecutionRecord; deathNotes cause param; RestoredAbility re-run mechanism)
exp-demons-B DONE (characters.json drifted from raw_*.json — Riot proven; DeathPipeline block/consequences; validateSetupState per-character table; Riot/Leviathan jinx rule uncertainty)
ux/day-screen DONE (48 findings; day timeline + bottom bar + pinned seat ring; Execution.resolve pipeline; ExecutionRecord; timer destroyed by tab switch; phase button placement)
ux/grimoire-and-seats DONE (circle overlap at 12+ seats / tokens vanish at 13-16; 6sp labels; ReminderKind pips + Board view; engine placeReminder; KillSheet; Player.history; PrivacyCover not topmost; notes discarded w/o Save)
ux/setup-and-home DONE (SetupTasks declarative table; bluffSets map; single-slot save destroys game/log; RevealFlow Ogre red card; PWA build id/visualViewport)
status-model DONE (1116 lines; Effect model w/ endsWithSource + impairment recursion; killOutcome 15-step precedence; ExecutionRecord; DeathEvent + Prompt queue; 40-row on-death table; 47 tests; 3 disagreeing impairment predicates to collapse)
night-engine DONE (1372 lines; NightPlan pure fn of state, StepKey(id,playerId,variant), WakeStyle.FIRST_NIGHT, StepGate/NightAction/NightEffect/TokenRule per character, ChoiceRecord, Reduced gate for Exorcist, DawnReport before clearEphemeral; generic SeatSheet tokens have sourceId '' = immortal + poison forever — likely DA root cause; multi-copy tokens data fix)
ux/night-screen DONE · travellers-A DONE
## Phase 2a digests launched (12): tb-townsfolk, tb-evil-and-sv-demons, bmr-townsfolk, bmr-evil-and-outsiders, sv-townsfolk, sv-evil-outsiders-and-exp-demons-a, exp-townsfolk-ab, exp-townsfolk-cd, exp-outsiders, exp-minions, exp-demons-b-and-travellers-b, travellers-a → docs/audit/digest/<group>.md (brief: DIGEST-BRIEF.md)
- [ ] digest fabled (after fabled-A finishes; fabled-B files already exist)
## Phase 2b (after setup-identity, data-accuracy, friction-log finish): ARCHITECTURE reconciliation agent reads the 6 mechanics + 5 ux specs → docs/audit/ARCHITECTURE.md (canonical types, resolving name conflicts: LedgerEntry vs DayEntry, ExecutionRecord shapes, Effect model, NightPlan, SetupTask, bluffSets) → then PLAN.md with work packages + disjoint file ownership → Phase 3 implementation waves.
friction-log DONE (49 P0; 5 kill paths; memory deleted before use; 4 resolvers; 12 principles) · fabled-A DONE (jinx set 58 vs 131 with verbatim missing list in djinn.md; FabledEntry state; Duchess data wrong)
- [x] digest fabled (launched)
Still running phase 1: setup-identity, data-accuracy. Then: ARCHITECTURE reconciliation agent.
setup-identity DONE (1555 lines; bluffSets Map + Bluffs.requirements; Player.grants/AbilityGrant + Identity.actingRoles; NightStep.holderId/abilityId/key; changeCharacter(); BagShape + SetupRequirements 26 rows; CORRECTIONS to char audits: Snitch×Marionette jinx retired, Riot no setup bracket, marionette 13-15p clause is real, alchemist/plaguedoctor text fine, drunk has no bracket, lunatic fake-minion count includes Marionette)
## Phase 2b: ARCHITECTURE agent launched → docs/audit/ARCHITECTURE.md (decision table, Kotlin types, file layout, work packages WP0-WP12 with disjoint ownership, migrations, open rules questions). data-accuracy still running.
data-accuracy DONE (official botc-release data: 181 roles/131 jinxes/nightsheet; characters.json regressed vs raw_*.json; Wraith missing + 9 new; multi-copy tokens collapsed = Pukka bug; 136 night_guide defects; labels Title Case). DECISIONS.md written (D1–D31) and sent to ARCHITECTURE agent.
Digests done: tb-townsfolk, tb-evil-and-sv-demons, bmr-townsfolk, bmr-evil-and-outsiders, travellers-a, exp-demons-b-and-travellers-b. Pending: sv-townsfolk, sv-evil-outsiders-and-exp-demons-a, exp-townsfolk-ab, exp-townsfolk-cd, exp-outsiders, exp-minions, fabled. NOTE: consolidate docs/audit/docs/audit/digest/* into docs/audit/digest/ when all done (replace symlinks with real files).
## Phase 2a COMPLETE: 13 digests consolidated in docs/audit/digest/ (9385 lines). DECISIONS.md D1–D53. IMPL-BRIEF.md written (phase 3 conventions).
Waiting on ARCHITECTURE.md → then PLAN.md (work packages) → Phase 3 waves: A = WP0 core types (in-tree, single agent) + WP5 data (worktree) · B = engine WPs in worktrees · C = registry-per-edition + UI screens · D = tests/playtests + integration + push after all 3 builds pass.
## Phase 3 started 2026-08-25. Audit corpus committed (7da2362). D54 approves ARCHITECTURE §6 defaults.
Wave 1: WP0 (in-tree, main branch) + WP5 (worktree) LAUNCHED. Next waves per ARCHITECTURE §4: W2 = WP1, WP4 (+WP12 continuous) · W3 = WP2, WP3 · W4 = WP6, WP7a–i, WP10, WP11 · W5 = WP8, WP9. Merge each worktree branch into claude/clocktower-grimoire-android-0bz090 after its report; verify 3 builds after each merge; push only at the end after full verification.
WP5 DONE in worktree branch worktree-agent-adad0faa2d40b58a5 (3 commits on top of 7da2362). NOT merged yet — merge after WP0 lands (3 data-pin test failures need WP4/WP12 edits; see FOLLOWUPS.md).
WP12 pass 1 DONE + MERGED (137 tests, 1 failing = WP4's riot, 8 @Ignore'd fixtures/gates each naming the WP that flips it on). Merger notes: Phases.kt expiry tables still use pre-WP5 labels (WP1 must fix — live-app bug until then); tools/playtests/full-game-storyteller-report.md stale (courtier/innkeeper, "Protected"); Teensyville fixture pending (WP12 pass 2).
WP4 DONE + MERGED: 206 tests, 0 failing, 8 skipped; uicheck + web green. Cross-package: GameActionsApi WP4 block filled; GameActionsTest lilmonsta (11 tokens/10 seats) + swap (shownCharacterId not transplanted, D33). Rulings pending: validateBag still List<String> (bagWarnings separate, protects WP11 SetupScreen); lunatic.minions blocking=false (no official "Fake Minion" token); Lil' Monsta seatless keyed off lilmonsta.noDemonSeat decision. Follow-ups: WP2 NightOrder.kt:40 → Identity.allActingRoles; WP8/WP10 nightRoleId callers NightScreen.kt:205, GrimoireScreen.kt:158; WP11 GameExtras.kt:430,496 Title Case + pass seatlessInPlayIds/virtualSetupCharacters into validateBag; GameActions.deal lacks lookup (use GameActionsApi.deal).
