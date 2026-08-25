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

## A marker nobody has seen fire is not a guard

`ArchitectureRulesSpec` asserts one thing per rule: the fixture directory is not
clean. That is enough to prove a rule works and useless for proving a *marker*
works, because a directory stays dirty on the strength of any single violation
in it. So when Kotlin 2.4 moved UUID generation into the standard library —
`Uuid.random()`, `Uuid.generateV4()`, `Uuid.generateV7()`, none of which the
ambient rules knew — adding the strings would have been an unverified claim of
exactly the kind this module exists to refuse. `AmbientMarkerSpec` names the
fixture it expects each new marker to flag.

All three markers went to `no-ambient-random`, and none to `no-ambient-time` —
which is a correction of the first attempt, kept here because the reasoning is
the useful part. `Uuid.generateV7()` really does read the wall clock, so a time
marker looked obviously right, and one was added. Writing the production
generator immediately disproved it. A file is spared `no-ambient-random` when it
implements `IdGenerator` and spared `no-ambient-time` when it implements `Clock`,
so `IdGenerator.Uuid7` — the one file in the tree that is *supposed* to mint a
time-ordered id — was the only thing the time marker flagged. Every illegitimate
call is already caught: the random marker `Uuid.generateV7` is deliberately
unparenthesised, so it matches `generateV7NonMonotonicAt` too, and no domain or
application file can mint a UUID by any route without firing it. A marker whose
entire effect is to accuse the correct implementation is not a guard, and the
alternatives — widening the time exemption by port name, or spending one of ten
`@ArchitectureException` slots — would both have bought that same protection
with more machinery.

`ambientRandomMarkersIn` exists so the deliberate breadth is asserted on a
literal snippet rather than argued about in a comment.

## SQL is scanned too, because the gates only read Kotlin

`own-schema-only` and `no-cross-schema-join` ask Konsist, and Konsist parses
Kotlin. So the cheapest way to break the rule they defend was never Kotlin at
all: a `create view` in a migration joins a neighbour's tables, and the consumer
starts depending on that neighbour's physical column names with nothing to notice.
`MigrationSchemaSpec` reads every `.sql` under `platform/` and `modules/` and
fails on a schema that is not the file's own.

The exemption is precisely the one §4.6 grants and no wider: a foreign key may
cross a schema, a query may not. `references identity.users (id)` and
`join jobs.companies` can sit in the same file, and only the second is a
violation.

The first draft matched every `x.y` in the file and reported table aliases as
schemas — `a.id` in `from applications.applications a`. The patterns are anchored
to the positions where a schema can actually stand (`from`, `join`, `table`,
`view`, `index … on` and their siblings), which is also why the spec asserts the
alias case rather than describing it. The decision this enforces, including the
half that cannot be enforced, is
[ADR-045](../../docs/adr/ADR-045-cross-capability-reads-own-the-copy.md).

## A gate that is not run is not a gate

Every rule above can be correct and still enforce nothing, and for a while all of
them enforced nothing. `Scopes.kt` finds the tree through `konsist.root`, a
**system property** — a `String`. Gradle tracks the string, not the thousands of
files behind it, so `:arch-tests:test` declared none of what it actually reads:
Kotlin under `platform/`, `app/` and `modules/`, every `.sql` those two trees
carry, and `docs/adr/`, which `RecordedException` opens to check an
`@ArchitectureException` cites a real file. A task with no inputs is never out of
date. It cached a pass and replayed it.

This was found by planting a violation rather than by reading the build script: a
`System.currentTimeMillis()` in `platform:observability` left `./gradlew check`
green, with `:arch-tests:test FROM-CACHE`, and `no-ambient-time` failed on the
same line the moment `--rerun` forced the task to actually run. The gate worked
throughout; nothing ever asked it. That is the worst shape a verification failure
can take, because a red build is a question and a green one is not.

The fix declares whole trees — all of `backend/` bar `build/`, `.gradle/` and
`.idea/`, plus `docs/adr/` — rather than a list mirroring `Scopes.kt` and
`MigrationSchema.kt`. A precise list is a second copy of a fact, and the same
"someone widens the scan and forgets to widen this" is what opened the hole in the
first place. Coarseness costs about seven seconds whenever anything under
`backend/` changes and keeps the cache when nothing does, which was measured both
ways: the planted violation now fails a plain `check`, and a second `check` with
no edits reports `UP-TO-DATE`.

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
`modules/` — `src/main/kotlin` only. Rules that care about tests as well
(`no-mock-libraries`, `usecase-has-test`, `port-has-conformance-suite`,
`no-verdict-in-signature`) run on a wider scope: `main`, `test` **and**
`testFixtures`. A `Fake` belongs in one of the latter two, never in
`src/main/kotlin`: nested `Jobs.Fake` still compiles into the production class
file.

`testFixtures` was added to that scope the moment the first double moved there,
and not as tidiness. Two holes open otherwise, both silent. `no-mock-libraries`
would stop looking exactly where a *shared* double lives — the one most worth
guarding, since several modules depend on it. And `port-has-conformance-suite`
would report a suite as missing because the suite had been moved somewhere the
scope did not reach, so the honest fix for a port would have looked like a
violation. A scope that lags behind where code actually lives turns its rules
into noise in one direction and blindness in the other.
