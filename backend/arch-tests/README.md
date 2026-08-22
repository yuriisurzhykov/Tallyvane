# arch-tests

Konsist rules for the backend. This is the machine that refuses a layer
crossing, a `*Utils` class, a MockK import, or an `@ArchitectureException`
without an ADR — not a catalogue of examples for a reviewer to remember.

`./gradlew arch` runs these tests after ktlint, detekt and the `modules.yaml`
graph check. There is no second command.

## Why a test module rather than detekt comments

Detekt already owns size and cyclomatic complexity. Putting architecture here
would mix two questions in one report. Konsist reads Kotlin PSI, so a rule can
ask "does this class live in `..port..` and is it an interface" without us
re-parsing the file. The rejected alternative was a home-grown visitor inside
detekt: it would drift from the Konsist rule codes that `@ArchitectureException`
already names.

## How a rule is wired

Each named code in ARCHITECTURE.md §15.3 is an `ArchRule`: a production scope
that must be empty, and a fixture directory that must not be. A rule that has
never been shown to fail is not a rule — it is a comment that happens to
compile. Fixtures live under `src/test/resources/konsist-fixtures/<rule-id>/`
and are parsed by Konsist, not compiled, so they can import types that do not
exist yet.

Adding a rule:

1. Give it a stable id (`no-fake-in-main`, not `fakesMustStayOutOfMain`).
2. Write the predicate in the matching `*Rules.kt` file and register it on
   `ARCH_RULES`.
3. Drop a Kotlin file under `konsist-fixtures/<id>/` that the predicate flags.
4. Run `./gradlew :arch-tests:test`. Both sides of the spec must pass: production
   clean, fixture dirty.

Skipping a violation is `@ArchitectureException(rule, reason, adr)` from
`platform:kernel`. The reason is at least forty characters, the ADR file must
exist under `docs/adr/`, and the project-wide count is capped at ten. Raising
the cap is a visible edit to the architecture tests, not a quiet annotation.

## What this module does not scan

`build-logic` and these tests themselves. Top-level helpers in `tallyvane.arch`
would fail `no-top-level-functions` if they were treated as production, and
Konsist's own types are not the product. Production is `platform/`, `app/` and
`modules/` — `src/main/kotlin`, plus `src/test/kotlin` for rules that care
about tests (`no-mock-libraries`, `usecase-has-test`). A `Fake` belongs in
those test directories, never in `src/main/kotlin`: nested `Jobs.Fake` still
compiles into the production class file.
