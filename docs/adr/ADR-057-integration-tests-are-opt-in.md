# ADR-057. Integration tests are their own source set, opt-in locally, mandatory in CI

## Status

Accepted.

## Context

Slices 7 to 10 verify behaviour only a real Postgres exhibits: the Exposed
`TransactionRunner` passing the same `TransactionRunnerConformance` as its fake
(ADR-046), migrations applying and re-applying to no effect, the schema-drift gate
comparing table definitions against a migrated schema, and the Postgres health check
reporting `Down` against a stopped database. H2 in Postgres-compatibility mode would
answer none of these honestly — a different dialect, no `citext`, no `create
extension`, different collation — so a green suite there would be a confident claim
about production with nothing behind it.

A real database costs resources on a developer machine, and the machine here does not
have them to spare. On Windows, Testcontainers means Docker Desktop and a WSL2 virtual
machine; the container itself is tens of megabytes, the VM is not.

§18.1 already named Testcontainers as the tool for this level.

## Decision

**Testcontainers, with `postgres:17-alpine`** — the image §16.4's compose file runs.
Not a community build of the same major version: musl and glibc disagree on text
collation, and a test passing on one build while failing on the other would be a
mystery with no cause a reader could find.

**A separate `integrationTest` source set**, added by `tallyvane.integration-test` only
to modules that have such tests, not by a shared convention plugin to all of them.
`integrationTest`, not `dbTest`: slice 13 boots a server against a live container, so
the seam is "needs something outside the JVM", not "needs a database".

**Not part of `check`.** Locally these run on request, `./gradlew integrationTest`; in
CI they are their own step. A task wired into `check` announces the exclusion on every
run, because a verification that quietly does not happen is worse than a red one.

**Fail, never skip, when Docker is absent.** These tests only run when asked for, so an
absent Docker means the request could not be honoured.

**One container per test JVM**, started lazily and left for Testcontainers' reaper.
That is one per Gradle test task, not one per build; a shared build service would give
the latter and is the escalation if the count ever costs more than the code would.

**The fixture hands out `DatabaseAccess`** — url, user, password — not a
`PostgreSQLContainer`. A test that held the container would have to be rewritten if the
database ever came from somewhere else. That type lives in the module's `main`, not in
`testFixtures`: slice 7's connection factory needs the same three fields, and a test-only
type would have guaranteed a duplicate beside it in production. This corrects the first
implementation, which put it in `testFixtures` — the exact case of a fixture shaping
production code that this decision otherwise argues against.

**Isolation between specs is a database per spec, cloned from a template** that has the
migrations applied once. Not implemented here: there are no migrations until slice 8.
Truncation was rejected because it needs a list of tables that a new table can be left
out of.

## Consequences

`org.testcontainers:postgresql` pulls only `org.testcontainers:jdbc`. The Kotest
extension for Testcontainers was rejected on measurement:
`io.kotest.extensions:kotest-extensions-testcontainers:2.0.2`, the latest, declares
`kotest-framework-api-jvm:5.5.4` against this repo's Kotest 6.2.4, and drags in
Kafka clients, Elasticsearch and HikariCP at compile scope. Twenty lines of our own
lifecycle cost less than that.

pgjdbc arrives here rather than in slice 7, because "connect and run `select 1`" needs
a driver.

`modules.yaml` now governs test edges as well as production ones, under the same rules:
`validateModuleGraph` reads `test*`, `integrationTest*` and `testFixtures*`
configurations alongside the compile ones. No second manifest key for test-only
permissions — if a test needs an edge its module may not declare, the test is in the
wrong layer, and an integration test of a use case belongs to `infrastructure`, where
`platform:*` is already allowed. Self-references are dropped: a module consuming its
own fixtures reaches nobody, and that case appears only once test configurations are
read.

Widening this immediately surfaced that CI ran no module tests at all. `./gradlew arch`
stops at the analysis gates, and completeness of `check` had rested on Gradle matching
a task name across projects — which the local invocation did and CI's did not. The root
`check` now depends on every leaf's `check`, so completeness is a property of the task
graph rather than of how the build was invoked, and CI calls `check`.

Parity between the test image and production is not yet enforced by anything: there is no
compose file in the repository, so `postgres:17-alpine` appears in the fixture and in a
line of `ARCHITECTURE.md`, and the version spec compares a constant to a constant. The
slice that creates compose owns making the tag have one home and adding a check that the
two agree.

Two of the four specs written here were deleted in the same slice, and the reasons are the
useful part. One asserted the shape of a url Testcontainers produces — unfailable by our
code. The other asserted that the fixture returns the same database twice, which
contradicts the per-spec cloning decided above; a test that must be deleted for the plan
to proceed reads like a decision. What is left is a canary and a parity check, and neither
is coverage of Tallyvane — which is worth stating plainly so that the count of tests here
is never mistaken for one.

## Alternatives considered

**Embedded Postgres** (`io.zonky.test:embedded-postgres`, with Windows binaries for
17.x published). A real Postgres with no Docker at all, which fits the resource
constraint better than anything else here. Rejected on fidelity: it is not the image
production runs, so the collation caveat above applies in the other direction.

**A database already running, addressed by an environment variable.** No machinery, and
still the fallback the `DatabaseAccess` shape keeps open. Rejected as the default
because a suite would run against a database whose state someone else may have changed,
and the Postgres version would be declared in two places.

**Never running them locally.** Rejected as the default in favour of opt-in: the tests
have to be runnable by the person writing them.

**A Gradle shared build service for one container per build.** The only mechanism that
matches "one container for the whole build", which is what the plan originally said.
Deferred: only one module has such tests today, so it would buy nothing measurable yet.

**Reusable containers** (`withReuse`). Survives across builds, needs a file in each
developer's home directory, and is normally disabled in CI — a different behaviour
locally and remotely is the wrong thing to introduce here.
