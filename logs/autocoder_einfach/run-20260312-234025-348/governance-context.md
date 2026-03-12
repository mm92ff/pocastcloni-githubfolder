# Governance Context

- Enabled: True
- Profile: compact
- Loaded files:
  - AGENTS.md: C:\Users\jemi\Desktop\Github\autocoder_2\autocoder_einfach\governance\profiles\compact\AGENTS.md [profile:compact]
  - SOUL.md: C:\Users\jemi\Desktop\Github\autocoder_2\autocoder_einfach\governance\profiles\compact\SOUL.md [profile:compact]
  - MEMORY.md: C:\Users\jemi\Desktop\Github\autocoder_2\autocoder_einfach\governance\profiles\compact\MEMORY.md [profile:compact]

## Combined Prompt Block

Repository governance context (must be respected):

### AGENTS.md [profile:compact] C:\Users\jemi\Desktop\Github\autocoder_2\autocoder_einfach\governance\profiles\compact\AGENTS.md
# Agent Instructions - AutoCoder Simple

## Read Order

Before implementation work, read:
1. `AGENTS.md`
2. `SOUL.md`
3. `MEMORY.md`

## Workspace Boundary

Operate only within the selected target workspace and this AutoCoder Simple repository.
Do not perform unrelated edits outside requested scope.

## Workflow Contract

The workflow has fixed role phases:
1. Evaluation agent analyzes task and plan.
2. Implementation agent applies code changes.
3. Review agent(s) validate correctness and coverage.

If review is denied, implementation must address review findings and retry.

## Safety Rules

- Do not create commits unless explicitly requested.
- Keep changes scoped to the active task/iteration.
- Report checks run and unresolved risks.
- Treat missing tooling as a reported condition, not a crash reason.

## Output Expectations

- Evaluation output: summary, plan, acceptance checklist, risks.
- Implementation output: changed files, what was fixed, checks, open risks.
- Review output: strict JSON only with fields `verdict`, `reason`, `required_fixes`.

## Coding Rules

- Prefer small, explicit, maintainable changes.
- Preserve existing architecture unless refactor is necessary and justified.
- Keep logs and user-facing behavior clear.

### SOUL.md [profile:compact] C:\Users\jemi\Desktop\Github\autocoder_2\autocoder_einfach\governance\profiles\compact\SOUL.md
# AutoCoder Simple - Project Soul

## Identity

AutoCoder Simple is a desktop workflow orchestrator for iterative AI-assisted code changes.
It is not a full autonomous issue bot. It is an operator-controlled GUI that runs:
Evaluation -> Implementation -> Review (single or multi-reviewer) in loops.

## Primary Goal

Provide a stable, transparent, and debuggable local workflow for code iteration runs with:
- explicit task prompts,
- clear runtime status,
- reproducible logs/artifacts,
- editable prompt and task profiles.

## Scope

- In scope: workflow orchestration, prompt/profile management, run logging, summary generation.
- Out of scope: automatic GitHub issue lifecycle, forced git commits, hidden automation side effects.

## Architecture Principles

1. Keep UI, state, prompt storage, and workflow execution separated.
2. Prefer explicit state over implicit magic.
3. Preserve backward compatibility for stored JSON state where practical.
4. Never block the UI thread during workflow execution.
5. Keep the workflow deterministic from current UI state at start time.

## Prompt Principles

1. System prompts define role behavior (Evaluation / Implement / Review).
2. Task profiles define work units (Iterations tab data).
3. Governance context (AGENTS/SOUL/MEMORY) is advisory/constraint context for agents.
4. Prompt templates use placeholders and must remain readable after substitution.

## Quality Bar

- Application must start without crashing.
- Missing optional files must not crash execution.
- Every run must produce understandable logs and a summary.
- State persistence should survive restarts (window + workflow settings).

## Language Policy

All product-facing UI text, docs, prompts, and comments should be English unless a user explicitly requests another language.

### MEMORY.md [profile:compact] C:\Users\jemi\Desktop\Github\autocoder_2\autocoder_einfach\governance\profiles\compact\MEMORY.md
# AutoCoder Simple - Project Memory

> Living document. Update after meaningful workflow, architecture, or behavior changes.

## Current State

- Desktop app with tabs: Workflow, Iterations, System Prompt, SP_2, Live Log, Summary, Help.
- Prompt templates and task profiles are stored as JSON in `data/prompts/`.
- Review supports multiple reviewers per attempt.
- Quick Prompt mode supports single-iteration execution from Workflow tab.
- Implementation attempt loop rejects no-op attempts (no meaningful workspace delta).
- Added Python task profiles for `analysis-only` and `implementation-with-checks` usage.
- Default workflow safety tightened (`require_clean_workspace=true`, lower repeated-failure abort threshold).
- Workflow start now auto-bootstraps a minimal Python pytest file when required pytest checks are active and no tests exist.
- Added `Bootstrap Environment` action in Workflow tab: prepares baseline Python tests/tooling and applies strict mode defaults.
- Added `Git` tab with tracked-file/folder tree for the active target repository.
- Added `Git` tab action to edit and apply `.gitignore` content directly for the active target repository.
- Added `Git` tab action to untrack already tracked entries that are now ignored by `.gitignore`, then optionally create a dedicated commit (`Commit Untracked Ignored`).
- Added `Git` tab action to stage and optionally commit newly discovered untracked files that are not ignored by `.gitignore` (`Commit New Untracked Files`).
- `Track New Files + Commit` now handles pre-existing staged changes with explicit choices (commit staged first, unstage staged, combine staged+new, cancel).
- `Git` tracked-file tree now supports right-click `Untrack + Commit` on individual file/folder entries.
- `Git` tracked-file tree now also supports right-click `Ignore + Untrack + Commit` to add `.gitignore` entry and commit untrack in one step.
- `Git` ignored-file tree now supports right-click `Remove From .gitignore + Commit` to remove matching `.gitignore` entries with a dedicated commit.
- `Git` untracked-file tree now supports right-click `Track + Commit` and `Ignore + Commit`.
- Added second-row `Repo Health Check` action in `Git` tab to validate git availability, repo state, identity setup, and clean/dirty workspace signals.
- Added second-row `Commit Modified + Commit` action in `Git` tab to commit unstaged tracked changes as a dedicated commit.
- `Git` tab now renders three side-by-side trees for tracked, ignored, and untracked entries with per-column file/folder counters.
- Added `Git` tab action to update configured `.gitignore` baseline entries and commit `.gitignore` directly.
- `Iterations` tab list now uses per-row checkboxes for enabled/disabled state and includes `All On` / `All Off` actions in edit mode.

## Key Decisions

| Decision | Choice | Why |
|---|---|---|
| Runtime mode | Background worker thread | Keep UI responsive |
| Prompt storage | JSON files under `data/prompts/` | Editable, portable, versionable |
| System prompt editing | Allowed in UI | Fast iteration without code edits |
| SP_2 preview | Placeholder rendering with highlight | Improve transparency of effective prompts |
| Check presets | Language-based JSON presets | Avoid hardcoded command logic |

## Known Risks

- Prompt quality depends on user-authored content.
- Missing external agent CLIs can block run start.
- Very large prompt contexts can increase token usage and latency.

## Next Improvements

- Keep governance context lightweight to avoid prompt bloat.
- Continue splitting large modules when responsibilities grow.
- Improve summary detail for changed files and checks.

## Update Rules

1. Record important behavior changes.
2. Record new defaults and migration choices.
3. Remove stale statements that no longer match the code.
