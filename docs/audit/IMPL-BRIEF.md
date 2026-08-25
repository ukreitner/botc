# Phase 3 — implementation brief (read fully before touching code)

You are implementing ONE work package (WP) of a large, audited redesign of the Clocktower
Grimoire storyteller app. Other agents implement other packages in parallel in separate git
worktrees; the lead merges. Discipline about file ownership is what makes that work.

## Required reading, in order
1. docs/audit/ARCHITECTURE.md — the canonical design: types, file layout, your WP's section
   (files owned, depends-on, acceptance criteria). It overrides everything else.
2. docs/audit/DECISIONS.md — binding lead rulings (D1–D53). If ARCHITECTURE and a DECISION
   disagree, DECISIONS wins; note it in your final report.
3. The mechanics spec(s) your WP implements (docs/audit/mechanics/*.md — only the sections
   ARCHITECTURE points you to) and, for per-character work, the digest cards
   (docs/audit/digest/<group>.md — ≤70 lines per character; the long files under
   docs/audit/characters/ are only for when a card is ambiguous).
4. The existing code you will change. Read it before editing; keep its style (immutable
   GameState, pure GameActions returning new state, kotlinx.serialization with defaults for
   every new field so old saves load, no Android APIs inside engine/).

## Rules
- Edit ONLY the files your WP owns (listed in ARCHITECTURE.md). If you truly need a change
  elsewhere, make the smallest possible additive change, list it explicitly in your report
  under "cross-package edits", and never reformat/reorder other code.
- Every new `GameActions`/engine verb the UI calls must be exposed through the shared
  view-model interface (see ARCHITECTURE.md) so Android (`app/.../GameViewModel.kt`) and web
  (`web/.../WebGameViewModel.kt`) both compile. If your WP is the one introducing the
  interface, say so; otherwise add your methods in your WP's designated block only.
- Reminder labels: official Title Case from characters.json; comparisons case-insensitive;
  never match rules by label substring.
- No new dependencies. No network at runtime. Compose Multiplatform-safe code only (the same
  UI sources compile to wasm): no android.* imports in shared UI, use the Platform seams.
- Tests: add engine tests (JUnit4, `engine/src/test/kotlin/...`) for every rule you implement;
  the digest cards list the Given/When/Then cases. Existing tests must still pass unless the
  spec explicitly retires a behaviour (then update the test and say so).

## Build & verify (run ALL that apply before you report; fix what you break)
    export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
    ./gradlew :engine:test                       # engine (always)
    ./gradlew -p tools/uicheck compileKotlin     # Compose UI typecheck (if you touched app/)
    ./gradlew -p web wasmJsBrowserDistribution   # PWA build (if you touched app/ or web/)
Gradle runs from several worktrees at once can contend; if a build fails on a lock/daemon
issue, retry once with `--no-daemon`. Never run `git clean`, never delete build dirs of
other worktrees.

## Git
- Work in the worktree/branch you were given. Commit early and often with clear messages
  (`WPn: <what>`); end each commit message with
  `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Do NOT push. Do NOT merge. Do NOT rebase onto other branches. The lead merges.
- Do not touch docs/audit/ except to append a short "IMPLEMENTATION NOTES" section to your
  WP's section in docs/audit/PLAN.md is NOT allowed either — put notes in your final report.

## Final report (return value) — terse
- Branch name + commit list (one line each).
- Files changed (owned) / cross-package edits (with justification).
- Which acceptance criteria are met; which are not and why.
- Build/test results verbatim (pass counts, failures).
- Anything the merger must know (migrations, renamed symbols, follow-ups for other WPs).
