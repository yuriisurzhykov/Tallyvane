# verification — `tallyvane.verification`

`tallyvane.gradle.verification.VerificationPlugin` registers the backend
`arch` aggregator (ktlint and detekt on every leaf, `:arch-tests:test`,
`validateModuleGraph`, and this included build's `check`) and makes `check`
depend on `arch` **and on every leaf's own `check`**.

That last part was missing, and its absence was invisible locally. Gradle matches a
task name across all projects, so `./gradlew check` from the backend root ran each
module's tests by name matching rather than by dependency — while CI ran
`./gradlew arch`, which stops at the analysis gates. Measured with `--dry-run`:
`arch` includes zero `:platform:*:test` tasks. Backend unit tests therefore ran on
no push at all. Depending on the leaves makes completeness a property of the task
graph instead of a property of how the build was invoked, and CI now calls `check`.

`integrationTest` is deliberately outside that graph: it needs Docker, so it is
opt-in locally and a separate CI step (ADR-057). Each module that has such tests
prints, during `check`, that they were excluded.

There is no domain here: it is Gradle wiring. `tallyvane.root` applies it.
