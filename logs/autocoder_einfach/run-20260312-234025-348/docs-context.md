# Docs Context

- Enabled: True
- Profile: compact
- Code language: kotlin
- Loaded files:
  - core.md: C:\Users\jemi\Desktop\Github\autocoder_2\autocoder_einfach\docs_context\profiles\compact\core.md [profile:compact]
  - language_kotlin.md: C:\Users\jemi\Desktop\Github\autocoder_2\autocoder_einfach\docs_context\profiles\compact\language_kotlin.md [profile:compact]

## Combined Prompt Block

Project docs context (apply when relevant):
- Profile: compact
- Code language: kotlin

### core.md [profile:compact] C:\Users\jemi\Desktop\Github\autocoder_2\autocoder_einfach\docs_context\profiles\compact\core.md
# Core Docs (Compact)

Use this context for every language:
- Keep changes minimal and in-scope.
- Prefer readable, maintainable code over clever tricks.
- Preserve existing architecture unless change is necessary.
- Report which checks were run and the result.
- If checks cannot run, explain why.

Output guidance:
- List changed files.
- Explain what was fixed.
- Mention open risks.

### language_kotlin.md [profile:compact] C:\Users\jemi\Desktop\Github\autocoder_2\autocoder_einfach\docs_context\profiles\compact\language_kotlin.md
# Kotlin Docs (Compact)

- Prefer null-safety and explicit types at boundaries.
- Keep Gradle tasks green (`test`, lint/static checks).
- Keep data classes and domain logic separated.
