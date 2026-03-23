# One-Shot Start Template

Use or adapt this message to start the full automatic workflow (Python or Kotlin).

```text
Use the iterative-autocoder-style-workflow skill.

Read `workflow/runtime/app_state.json` and resolve `profile_path`.
Load that profile from `.claude/profiles/...`.
Work only on iterations where `enabled` is `true`.
Use the profile's `code_language` to apply language-appropriate checks and style (`python` or `kotlin`).

If the project is not a git repo, initialize git locally, create a suitable `.gitignore`, and create a local baseline commit.
For each active iteration, explicitly spawn `evaluator`, then `implementer`, then `reviewer`.
If review returns `DENIED`, feed `required_fixes` back to `implementer` and re-run `reviewer`.
After each accepted iteration, create a local commit.
Use local git only. Never push.
```

Tip:
- For single-iteration runs, you can usually keep this same bootstrap prompt and set only one iteration to `enabled: true` in the selected profile JSON.
