# Install and Run — Claude Code Edition

## 1. Copy into your project root

Copy this bundle so your repository contains:

```text
your-project/
  CLAUDE.md          ← main instruction file (replaces AGENTS.md)
  SOUL.md
  MEMORY.md
  .claude/
    agents/
      evaluator.md
      implementer.md
      reviewer.md
    skills/
      iterative-autocoder-style-workflow/
        SKILL.md
        review_schema.json
        scripts/
          render_prompt.py
          workflow_reporter.py
    profiles/
      task_profile_python_best_practices_iterative.json
      iterations_manual.json
  workflow/
  docs_context/
  governance/
  docs/
```

> **Important:** Everything goes into the root of the project you want to improve — the same project Claude Code will work on. Claude Code reads `CLAUDE.md` and `.claude/` from the project root automatically.

## 2. Open the project in Claude Code

```bash
cd your-project
claude
```

Claude Code automatically picks up `CLAUDE.md` and `.claude/agents/` on start.

## 3. Optional: inspect workflow status

```bash
python .claude/skills/iterative-autocoder-style-workflow/scripts/workflow_reporter.py --write-report
```

This writes `workflow/reports/bootstrap_status.json` with enabled areas, git status, and suggested next actions.

## 4. Optional: render a ready-to-paste prompt

```bash
python .claude/skills/iterative-autocoder-style-workflow/scripts/render_prompt.py
```

Use `--only-title "Python Best Practices: Validation and Business Logic"` to render a single-area prompt.

## 5. Start the workflow in Claude Code

Paste one of the prompts from `docs/PROMPT_EXAMPLES.md` into the Claude Code terminal.

## 6. Invoke subagents explicitly or let Claude delegate

- **Explicit:** type `@agent-evaluator`, `@agent-implementer`, `@agent-reviewer` in your prompt
- **Automatic:** Claude Code will delegate automatically based on each agent's description when you use the full orchestration prompt

## Default run behavior

The bundle runs one-shot by default:
- automatically initialize local git if missing
- automatically create `.gitignore` if missing
- automatically create a local baseline/bootstrap commit when practical
- automatically run evaluator → implementer → reviewer in sequence
- automatically create local commits for accepted iterations
- **never push**

## Enable additional Python best-practices areas

Edit `.claude/profiles/task_profile_python_best_practices_iterative.json` and set `"enabled": true` for the areas you want to activate.
