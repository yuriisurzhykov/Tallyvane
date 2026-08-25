# platform:persistence

Everything about reaching Postgres: the connection, the transaction boundary, schema
conventions, and migrations. Almost none of it exists yet — slice 6 built only the test
harness, because the four slices that follow verify behaviour no in-memory substitute
exhibits, and they cannot be written before there is a real database to write them
against.

## Why the harness came before the code

`TransactionRunnerConformance` is the reason. ADR-046 exists so a fake and a real
adapter cannot drift; that promise is empty until the same suite runs against Exposed
over a real Postgres. Slice 8 needs migrations to apply and to re-apply to no effect;
slice 9 compares Exposed's table definitions against a migrated schema; slice 10 asks
whether the health check says `Down` when the database is stopped. None of those is
answerable against H2 in Postgres-compatibility mode: a different dialect, no `citext`,
no `create extension`, different collation. A green suite there would be a confident
claim about production with nothing behind it — which is the one failure this repository
spends most of its machinery refusing.

## What was actually done

`PostgresFixture` starts one `postgres:17-alpine` container per test JVM, lazily, and
leaves it to Testcontainers' reaper. The image is the one §16.4's compose file runs, and
that is not incidental: musl and glibc disagree on text collation, so a community build
of the same major version could pass a test that the shipped image fails, with no cause
a reader would find.

It hands out `DatabaseAccess` — url, user, password — rather than the container. The
mechanism is meant to be replaceable: addressing a database that is already running, by
environment variable, stays possible without touching a single spec. A test holding a
`PostgreSQLContainer` would have to be rewritten for that.

That type lives in `main`, and a first draft had it in `testFixtures`. The correction is
worth keeping because the reasoning generalises: slice 7's connection factory needs those
same three fields, so a test-only type would have guaranteed a second one beside it in
production — two vocabularies for one concept, free to drift. A fixture that quietly
shapes production code is the most expensive kind of test scaffolding, and putting the
value where production will use it is what stops that here.

Two of the four original specs were deleted for related reasons. One asserted that the
returned url contains `jdbc:postgresql://` — a string Testcontainers produces, which no
bug of ours can fail. The other asserted that a second call hands back the same
database, which contradicts the isolation already agreed for slice 8: a database per
spec, cloned from a migrated template. A test that must be deleted for the plan to
proceed reads like a decision, and is worse than no test.

What remains is two specs, and neither is coverage of Tallyvane. `select 1` is a canary
for this environment — Docker, the image, the driver, the wiring — and it cannot fail
because of anything we write. The version check is the one claim a future change can
quietly break, and it is currently weaker than it looks: it compares a constant in the
fixture against a constant in the spec, while the real source of truth is a compose file
that does not exist yet. Parity between the test image and production therefore rests on
a line in `ARCHITECTURE.md` and nothing mechanical. When compose lands, the tag gets one
home and a check compares the two.

The tests live in an `integrationTest` source set that `tallyvane.integration-test` adds
to this module, and they are not part of `check`. Running them costs a container, and
the machine this is developed on does not have resources to spare, so locally they are
opt-in — `./gradlew integrationTest` — and in CI they are their own step. A task wired
into `check` prints that they were excluded on every run: a verification that quietly
does not happen is worse than one that fails.

The Kotest extension for Testcontainers was rejected on measurement rather than taste.
`io.kotest.extensions:kotest-extensions-testcontainers:2.0.2` is the latest release and
declares `kotest-framework-api-jvm:5.5.4` while this repo is on 6.2.4 — the version
boundary where the extension API changed — and it pulls Kafka clients, Elasticsearch and
HikariCP at compile scope to start a Postgres container. `org.testcontainers:postgresql`
pulls one thing, `org.testcontainers:jdbc`.

Isolation between specs is decided but not built: a database per spec, cloned from a
template with the migrations already applied. That needs migrations, so it lands in
slice 8. Truncation was rejected — it needs a list of tables, and a new table can be
left out of a list.

Full record, including what was rejected and why:
[ADR-057](../../../docs/adr/ADR-057-integration-tests-are-opt-in.md).

## Why it is understandable, scalable, extensible

A module that needs a database in tests applies one plugin and takes
`testFixtures(platform:persistence)`. It learns nothing about Docker, container
lifetimes or image tags. When the fixture's mechanism changes, no spec changes.

The seam is named for what it guards — "needs something outside the JVM" — so slice 13's
server tests belong in the same source set without a second one appearing next to it.

## Fault tolerance

There is no graceful degradation here on purpose. When Docker is absent these tests fail
rather than skip: they only run when someone asked for them, so an unmet request is a
failure, and a silent pass would be the worst of the three outcomes.

## The SOLID angle

Dependency inversion is the whole of `DatabaseAccess`: consumers depend on how to reach
a database, never on what started it. Single responsibility separates the fixture, which
owns a container's lifetime, from the value, which owns nothing — which is why replacing
the first leaves the second untouched. Interface segregation is why the value carries
three fields and not a `Connection`, a `DataSource` and a pool: each consumer opens what
it needs, and slice 7's pool does not become everyone's dependency.
