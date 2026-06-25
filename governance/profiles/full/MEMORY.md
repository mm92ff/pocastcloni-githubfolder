# Project Memory (Full)

> Fuller workflow memory for Codex. Keep this aligned with `AGENTS.md` and root `MEMORY.md`.

## Current State

- `AGENTS.md` is the single operative instruction file.
- Root `MEMORY.md` holds compact durable facts.
- There is no separate `SOUL.md`; the useful governance principles are folded into `AGENTS.md` and this memory file.
- Codex subagents live under `.codex/agents/`.
- The workflow skill lives under `.agents/skills/iterative-autocoder-style-workflow/`.
- Task profiles live under `.claude/profiles/` for compatibility with the protected profile selector and iteration manager.
- The active task profile source of truth is `workflow/runtime/app_state.json`.
- The current active profile is the Kotlin simple-app profile with 12 enabled areas.

## Workflow Contract

- Phase order is Evaluation -> Implementation -> Review.
- Evaluation and Review are read-only.
- Review returns strict JSON only.
- If review denies the patch, implementation addresses `required_fixes` and review runs again.
- Work stays limited to the active iteration.
- Adjacent issues are recorded as follow-up items, not absorbed into the active scope.

## Bootstrap Behavior

- Git initialization and local commits are governed by `AGENTS.md` and the active workflow skill.
- Workflow reports can be written to `workflow/reports/`.
- Never push, alter remotes, or run destructive git operations unless explicitly requested.

## Recommended Future Additions

- Primary build/test commands.
- Generated file policy.
- Database migration policy.
- Protected directories.
- Release/signing expectations.
