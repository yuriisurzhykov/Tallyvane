# Databases in tests

## The same image production runs

Not H2 in PostgreSQL-compatibility mode. Different dialect, no `create extension`, different
collation behaviour — a green suite there is a confident claim about production with nothing
behind it.

Not a different build of the same major version, either, if it can be avoided: musl and glibc
disagree on text collation, so a test can pass on one build and fail on the shipped image with
no cause a reader would find. Pin the image tag the deploy uses, and keep the tag in one place
once a compose file exists.

## Fail when Docker is absent; never skip

A skipped test is a green build that verified nothing. If database tests are opt-in, then
asking for them and not having Docker is an unmet request — fail. The worst of the three
outcomes is a pass.

## Opt-in locally, mandatory in CI

Containers cost time and memory that a laptop may not have. A separate source set —
`integrationTest` via a test suite — keeps them out of the default `check` while staying
compiled, and CI runs them as their own step.

Two things make this safe rather than a place for rot:

The exclusion must announce itself. Wire a task into `check` that prints "integration tests
were not part of this check". Verification that quietly does not happen is worse than
verification that fails.

Completeness must be a property of the task graph, not of how the build was invoked. Gradle
matches a task name across projects, so `./gradlew check` from the root can pick up per-module
tests that a CI command like `./gradlew arch` never triggers — a discrepancy that hides the
fact that no test ran at all. Make the aggregate depend on the leaves and check the graph with
`--dry-run` rather than assuming.

## A database per test, cloned from a template

Truncation between tests needs a list of tables, and a table added later can be left out of
that list silently. Instead:

1. once per JVM, create a template database and apply all migrations to it;
2. per test, `create database spec_n template the_template` — this copies files rather than
   replaying migrations, which is what makes it affordable per test;
3. connect to `spec_n`.

Constraints worth knowing before writing it: PostgreSQL refuses to clone a template while any
session is connected to it, so build it and leave it alone; `create database` must be issued
from another database and cannot run inside a transaction; and database-level settings are
*not* copied, so any guarantee resting on `ALTER DATABASE … SET` is absent in every clone.

Offer two shapes, because tests about migrating need a virgin database while everything else
wants the schema:

```kotlin
fun empty(): DatabaseAccess      // nothing applied
fun migrated(): DatabaseAccess   // clone of the migrated template
```

**Assert the isolation.** Every other test rests on the claim that its database is its own, so
prove it: two calls return different databases, and a table created through one is invisible
through the other.

## Hand out connection details, not the container

Return a value — url, user, password — rather than a `PostgreSQLContainer`. A test holding the
container has to be rewritten the day the database comes from somewhere else: an already-running
instance addressed by environment variable, a service container in CI, a compose service. Put
that value type in the production source set if production code will need the same three
fields, or a duplicate appears there and the two drift.

## What a database test can and cannot prove

**Observe with something independent of the code under test.** Counting rows through the same
ORM the test is exercising proves only that the layer agrees with itself. Open a plain JDBC
connection for the assertion.

**A test that suspends proves nothing about code that blocks.** Coroutine cancellation is
cooperative, so a double built on `delay` never exercises the case where a JDBC call holds its
thread. If a timeout or a bound is being asserted, the double must block — `Thread.sleep` — or
the assertion is about the double.

**Take the guard away and watch it fail.** Before counting a test as coverage, ask which bug
would fail it, then remove the thing it guards and confirm it does. A test that passes both
with and without the guard is documentation of an intention, not a check.

**Name a canary a canary.** A test that connects and runs `select 1` verifies Docker, the
image, the driver and the wiring, and cannot fail because of a bug in the application. It is
worth having and it is not coverage; say so where it lives, so a count of green tests is never
mistaken for a guarantee.

**Prove the guarantee on the artefact that ships.** A guarantee asserted only against a freshly
migrated database can be absent in the cloned databases every other test uses — this is exactly
how a `search_path`-dependent guarantee survives a whole suite while being broken. Assert it
against the clone as well.

## Fakes, not mocking libraries

A handwritten fake can be made to pass the same conformance suite as the real adapter, which is
what stops the two drifting; a mock cannot, and will not fail the day the port grows a second
method. Write one suite per port, run it against both the fake and the real implementation, and
let the first real implementation be where the suite earns itself — that is when a difference
the fake never had shows up.

"Fresh" belongs in that suite, not in a framework flag: for PostgreSQL it means an empty table,
which no isolation setting arranges on its own.
