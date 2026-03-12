# Implementation
## Changed Files
- `app/src/main/java/com/example/pocastcloni/di/CoroutinesModule.kt`

## What Was Fixed
- Switched the dispatcher provider setup to a `@Binds` binding so `DefaultDispatcherProvider` can be injected as a singleton with `@Inject constructor`, removing the manual `@Provides` instantiation and aligning with DI best practices.
- Left the dispatcher qualifiers, coroutine scope provider (with `SupervisorJob` + default dispatcher), and scope annotations unchanged so `@ApplicationScope` injection continues to deliver a supervisor-backed, default-dispatcher scope.
- Documented two failure scenarios for this focus area: (1) an application-scoped coroutine scope without a `SupervisorJob` would cancel unrelated startup work when one child fails—expectation is a supervisor scope using `Dispatchers.Default`; (2) ambiguous dispatcher bindings could inject `Dispatchers.Main` into I/O-heavy work—expectation is clear qualifier usage (`@DefaultDispatcher`, `@IoDispatcher`, `@MainDispatcher`) for each consumer.

## Checks Run
- Not Run (not requested for this iteration -> please execute `./gradlew test` and `./gradlew assemble`; consider optional checks `./gradlew lint`, `./gradlew ktlintCheck`, `./gradlew detekt`, `./gradlew spotlessCheck` if more validation is needed)

## Open Risks
- Existing workspace already contained many unrelated edits; ensure no conflicting changes when integrating this module update.
- Failure Scenario 1: Without the supervisor-backed `@ApplicationScope`, a single crash during initialization could cancel `AppInitializer`'s other coroutines—expected behavior is isolation via `SupervisorJob`.
- Failure Scenario 2: Missing dispatcher qualifiers could route background tasks to the main dispatcher, blocking the UI—expectation is that every injection point uses the correct qualifier (`Default`, `IO`, `Main`).
