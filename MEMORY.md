# MEMORY.md

> Short project memory. Keep this factual and update it only when durable workflow assumptions change.

## Current Workflow Shape

- `AGENTS.md` is the operative Codex instruction file.
- Codex subagents live in `.codex/agents/`:
  - `evaluator.toml`
  - `implementer.toml`
  - `reviewer.toml`
- The orchestration skill lives in `.agents/skills/iterative-autocoder-style-workflow/`.
- Task profiles live in `.claude/profiles/` because the protected local tools `select_profile.ps1` and `IterationManager.ps1` operate on that directory.
- The active profile is resolved from `workflow/runtime/app_state.json`.

## Active Runtime State

- Current active profile: `.claude/profiles/task_profile_kotlin_android_studio_simple_app.json`
- Current language context: Kotlin/Android
- Current active iteration: `Kotlin Simple App: Architecture and Package Boundaries`

## Stable Assumptions

- Phase order is fixed: Evaluation -> Implementation -> Review.
- Evaluation and Review are read-only.
- Review returns strict JSON only.
- Implementation must respect each iteration's `implementation_mode`.
- Missing tools are reported as missing, not installed automatically.
- Local git checkpoints are normal for full workflow runs, but never push or change remotes unless explicitly requested.

## Useful Context Files

- `docs_context/profiles/full/core.md`
- `docs_context/profiles/full/language_kotlin.md`
- `docs_context/profiles/full/language_python.md`
