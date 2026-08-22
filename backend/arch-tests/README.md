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

## Why a text rule reads code, not the whole file

Seven rules ask something no cheap PSI query answers — does this file mention
`Instant.now`, concatenate a quoted `SELECT`, name a foreign schema — and they
answer by scanning the file as text. Scanning `KoFileDeclaration.text` made the
gate punish accurate documentation: `Clock.kt`'s KDoc explains why
`kotlin.time.Clock.System` is banned and `IdGenerator.kt`'s explains why
`UUID.randomUUID()` is, so `no-ambient-time` and `no-ambient-random` failed on
the two port declarations for saying so.

The first reading was that the exemption had simply broken —
`implementsSimpleName("Clock")` asks Konsist for `classes()`, which never holds
an `interface`, so a port's own file was never exempt. That much is true, and it
is left alone on purpose: the predicate exists to exempt *implementations* of a
port, which are classes and which it matches correctly. Widening it to interfaces
would have hidden the real defect rather than removed it, because the next file
whose comment names a banned call would fail the same way — and a rule that
rewards vaguer documentation is worse than no rule.

So `SourceText.kt` hands every text-scanning rule a `codeText()` view with
comments stripped. String literals stay: `own-schema-only` reads schema names out
of `"jobs.job"` and `no-sql-concat` matches a quoted `SELECT`, so blanking
literals would disarm both. Two rules loosen as a consequence, deliberately —
`no-hardcoded-product-name` and `registry-owns-branching` no longer fire on a
comment, which is what they always meant.

`codeWithoutComments` is a lexer, so it has its own spec: a string helper without
tests fails silently and takes a whole gate down with it. It handles nested block
comments, raw strings and escaped quotes, and stops short in one documented
place — a comment inside a string template's interpolation counts as literal.
Konsist exposes no comment-free view of a file, and a full Kotlin lexer here
would be a second parser to keep alive.

## The SOLID angle

One reason to change `codeText()` — what counts as source a rule may inspect —
and it lives in one file, so no rule restates it. The rules stay closed against
that change: `noAmbientTime` names the concept, not the mechanics, and gained
nothing to maintain when nested comments were handled. Interface segregation is
why this is not a Konsist predicate on `KoFileDeclaration`'s many providers but a
single extension returning a `String`, and why the pure `codeWithoutComments`
is separate from the Konsist-bound `codeText()` — the lexer is testable without
constructing a file declaration at all.

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
