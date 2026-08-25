# platform:persistence

Everything about reaching Postgres: the connection, the transaction boundary, schema
conventions, and migrations. All four now exist in some form — the `Persistence` and
`Migrations` ports with `PostgresPersistence` and `FlywayMigrations` behind them — plus the
test harness that had to come before them, because the slices that follow verify behaviour
no in-memory substitute exhibits and cannot be written without a real database to write them
against.

Database access lives behind the port and its adapter. Nothing in `main` opens a
connection itself; raw JDBC appears only in integration tests, and there deliberately, so
an observation cannot be fooled by the machinery it observes.

## 2026-08-25 — the transaction adapter

Four facts shaped it, and none was assumed. `suspendTransaction` from `exposed-jdbc`
exists, in JetBrains' own words, "for compatibility with JDBC drivers, to call suspend
functions alongside blocking database operations" — the SQL under it still blocks its
thread, because JDBC returns a result and a result cannot be returned before it arrives.
Flyway has no R2DBC support and needs a JDBC URL, so pgjdbc stays in this project whatever
the adapter chooses. Exposed joins a nested transaction to its parent by default. Exposed
re-runs the whole block on `SQLException` by default.

So `suspend` in this port means "callable from a coroutine", not "costs no thread", and the
adapter's KDoc says exactly that. A signature that reads as a promise it does not keep is
what left the health-check timeout decorative for an entire evening one layer up.

Blocking work therefore runs only on a dispatcher whose parallelism equals the pool size.
That is not a style preference: with eight connections and unbounded IO, a burst of
transactions would park up to sixty-four shared IO threads inside `getConnection` and
starve health checks and model calls of them. Tying the two numbers together in one class
is what stops them drifting apart.

Nesting is refused rather than joined, and the reason is measured. A probe against a real
container showed a nested `suspendTransaction` reporting the same transaction id as its
parent — so an inner rollback would silently discard the outer's writes, while the fake
every other module tests against fails loudly on nesting. That is the disagreement ADR-046
exists to catch, and it caught it on the first run. The probe also showed
`TransactionManager.currentOrNull()` surviving a `withContext(Dispatchers.IO)` hop, which
is why asking Exposed is sound on a multi-threaded dispatcher and no coroutine-context
marker of our own was needed.

Two wrong turns are worth keeping. The conformance spec first arranged its table through
`suspendTransaction` and failed all seven cases with "no default database found", because
the arrangement ran before the pool that would have registered one — a setup depending on
the code under test having already worked. It now uses a plain JDBC connection, which also
fixed a second fault in the same place: the row count was being read through Exposed, so
the observation shared the machinery it was meant to judge.

The nesting guard was then removed on purpose to see what would fail. Exactly one case did,
the nesting one; the other six stayed green. A test that passes is not evidence until the
thing it guards has been taken away.

A third correction, made the same day and worth its own paragraph because it is a
different kind of mistake. `PostgresPersistence` was first written as a bare class
implementing nothing. It handed out a port — `TransactionRunner` — and that felt like
enough, but it is not the same thing as being one: a consumer wanting persistence had to
name the class whose insides are Hikari, Exposed and a JDBC url, so the choice was nailed
into every call site. The `Persistence` port fixes that, and the implementation is now
the only name in the module that says which technology it runs on.

`AutoCloseable` deliberately sits on the implementation and not on the port. A consumer
that merely runs transactions has no business being able to shut down a pool other code
is using; whoever built it closes it, which is the composition root. That is interface
segregation doing real work rather than being cited.

Full record, including the rejected R2DBC and virtual-thread routes:
[ADR-058](../../../docs/adr/ADR-058-transactions-over-jdbc.md).

## Why the harness came before the code

`TransactionRunnerConformance` is the reason. ADR-046 exists so a fake and a real
adapter cannot drift; that promise is empty until the same suite runs against Exposed
over a real Postgres. Slice 8 needs migrations to apply and to re-apply to no effect;
slice 9 compares Exposed's table definitions against a migrated schema; slice 10 asks
whether the health check says `Down` when the database is stopped. None of those is
answerable against H2 in Postgres-compatibility mode: a different dialect, no ICU collations,
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

## 2026-08-25 — migrations, and a database per test

ADR-051 had already fixed the layout. Three things it left open only appeared once the thing
ran, and one of them changed what the first migration had to contain.

**An extension can stop working silently, and the fix for that could too.** An extension's
operators live in the schema it was installed into. If that schema is not on `search_path`
when a query runs, PostgreSQL does not find them and compares as ordinary case-sensitive text
— no error, no warning. `citext` would simply stop being case-insensitive, and the symptom
would arrive as a person unable to sign in with their own address typed in a different case.

The first version of the migration answered that by installing `citext` into the platform
schema and setting `search_path` on the database, which covers every session including `psql`
and Flyway's own later runs. That was wrong, and the way it was wrong is the useful part.
Database-level settings are keyed to the database rather than stored in it, so `create
database … template …` does not copy them — and every integration database is a clone.
Measured: a cloned database reported `search_path` as `"$user", public` and could not resolve
the type at all. So the guarantee was absent precisely where it was being tested, and the
suite did not notice, because the only case touching a cloned database asserted that a schema
existed.

The answer now is an ICU collation rather than an extension: the migration creates
`platform.case_insensitive`, and columns declare it as `email text collate
platform.case_insensitive`. A collation is bound to the column when the table is created and
stored with it, reads nothing at query time, and travels with the schema when a database is
cloned. It cannot silently stop applying, where the previous arrangement could and did.
`citext` in `public` would also have worked — `public` is on every database's default
`search_path` — and was rejected because the guarantee would still rest on session state that
a later change could remove without a word.

The regression test is the one that matters: the case-insensitive comparison is asserted on a
**cloned** database, not only on a freshly migrated one, plus a case proving a unique index
refuses two addresses differing only by case. One limitation to know rather than discover:
PostgreSQL refuses `LIKE` on a non-deterministic collation, so case-insensitive prefix search
is `lower(email) like lower(…)`.

**Flyway's bookkeeping went into `platform`, and it is the only schema Flyway is told about.**
Listing every capability's schema would have been the "second place to forget" ADR-051
rejected for locations; instead each capability's first migration creates the schema it owns.
`platform` rather than a name of Flyway's own because `MigrationSchemaSpec` lets a migration
under `db/migration/platform/` name exactly that schema — one name instead of two.

**Two findings came from running the command rather than from tests.** `initSql` is deprecated
in favour of an `afterConnect` callback; letting Flyway create the one schema it needs avoids
both the deprecated setting and a callback class. And a second run printed `Schema version:
none`, which in a deploy log reads as "there is no schema" when it means "already up to date"
— Flyway leaves `targetSchemaVersion` null when it applied nothing. Both were shipped-and-
noticed only because the command was executed as the deploy will execute it, not merely
tested.

**Truncation is gone.** Each integration test now gets its own database: `empty()` for tests
about migrating, `migrated()` for everything needing the schema, cloned from a template
migrated once per JVM. `create database … template …` copies files instead of replaying
migrations, which is what makes it affordable per test. Truncation needed a list of tables
that a table added later could be left out of, silently.

That was not a hypothetical risk here. `FlywayMigrationsSpec` sets `search_path` **on the
database** — on a shared one it would have changed the environment every other spec ran in,
and it happened to be harmless only by luck. The isolation is asserted rather than trusted:
`PostgresFixtureSpec` proves two callers get different databases and that one's tables are
invisible to the other, because every other integration spec now rests on that claim.

Full record, including the rejected alternatives:
[ADR-059](../../../docs/adr/ADR-059-migration-application-and-test-isolation.md).

## 2026-08-25 — the schema drift gate

Tables are declared twice, in SQL for the migration and in Kotlin for Exposed, and nothing
makes the two agree. The gate turns that from a production failure into a build failure.

Which Exposed function to build it on was settled by two facts rather than taste.
`SchemaUtils.statementsRequiredToActualizeScheme` is deprecated in 1.x — under
warnings-as-errors it would not compile — and it reports only what the database is *missing*.
`MigrationUtils.statementsRequiredForDatabaseMigration`, from `exposed-migration-jdbc`, also
reports what the database has that no Kotlin table declares — but only on the tables it is
given. Drift on those columns runs both ways, and a gate watching one direction passes the
other half of the mistakes, which is why the spec asserts both.

That is not the whole of "what the database has that no Kotlin table declares." Measured
2026-08-25: `from(Aligned)` against a matching `aligned` plus an extra `orphan_leftover` in
the same schema returned empty. `MigrationUtils` partitions the supplied objects; it does
not enumerate the catalog. A review that pointed at that hole was right, and the planned
classpath scan in slice 13 would not have closed it — that scan still only feeds `from` the
objects it found.

`unmappedTables` is the third side. It reads `information_schema` over JDBC and returns
qualified names the supplied `Table` objects do not declare. Same-schema leftovers, leftovers
in another schema, and a leftover in `platform` that is not Flyway's history table are each
a case; ignoring the whole `platform` schema instead would hide the last of those. Flyway's
`platform.flyway_schema_history` is excluded by qualified name, and the migrated-database
case now asserts both methods stay silent on it.

`SchemaDrift` takes its database in the constructor and its tables per call, and the reason it
cannot simply find them all is structural: "unmapped" only means anything against a complete set, so a partial
list makes a neighbour's table look like something to drop. The only project allowed to see
every module's tables is `app` — `platform:*` may not depend on `modules:*` — so the run over
everything lands in slice 13, and discovery by classpath scanning arrives with it rather than
before it. That run calls `from` and `unmappedTables` together.

One case here exists purely for that future run: the gate is checked against a migrated
database, which carries `platform.flyway_schema_history` that no Kotlin table declares, and
reports nothing. Without it, slice 13's first full run could have failed on Flyway's own
bookkeeping and looked like drift.

What this leaves is honest to state: a verified mechanism with no consumer until slice 13. The
same shape as building `migrated()` a slice before anything needed it, with one difference —
there is no earlier home for the full run, because before `app` exists nowhere in the build may
see all the tables.

One correction worth keeping, because the reasoning generalises. `SchemaDrift` was first an
`object` with a single function taking everything as parameters — a `Utils` class under another
name. `no-stateful-objects` already forbids that shape, and the only reason it did not fire is
that the rule runs on `main` and this lives in `testFixtures`. Fixed by judgement rather than by
widening the rule: widening it would immediately flag `PostgresFixture`, whose whole purpose is
to hold one container for the JVM, so the principle here is care that does not depend on the
source set, not a rule that applies uniformly to every one of them.

Full record, including the per-module and `arch-tests` alternatives:
[ADR-060](../../../docs/adr/ADR-060-schema-drift-gate.md).

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
