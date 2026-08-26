# ADR-060. Schema drift is a build failure, detected in three directions

## Status

Accepted. The mechanism is built; the run over every table lands with `app`.

## Context

Tables are declared twice: in SQL for the migration and in Kotlin for Exposed. Nothing makes
the two agree. A column added to one and forgotten in the other is not a compile error — it
is a failure the first time a query touches that column, in production, on that path only.

Exposed offers two functions for comparing a live schema against table objects, and the
difference between them decides what a gate built on either can catch.

`SchemaUtils.statementsRequiredToActualizeScheme` is deprecated in Exposed 1.x, with an
annotation naming `MigrationUtils.statementsRequiredForDatabaseMigration` as its replacement
— so under warnings-as-errors it would not compile here anyway. It also answers only half
the question: it reports what the database is *missing*, and nothing about what the database
has that no Kotlin table declares.

`MigrationUtils` lives in `exposed-migration-jdbc`; the older `exposed-migration` artifact
stopped at a beta.

That still leaves a third side. `statementsRequiredForDatabaseMigration` partitions the
*supplied* table objects into existing and missing; it does not enumerate the catalog.
Measured 2026-08-25 against Postgres 17: a table `orphan_leftover` sitting next to a matching
`aligned` produced an empty list from `from(Aligned)`. A migration that leaves a table behind
after its Kotlin `Table` is removed would pass a gate built on `from` alone, including the
planned classpath scan — that scan still only feeds `from` the objects it found.

## Decision

**`from` is built on `MigrationUtils.statementsRequiredForDatabaseMigration`.** Empty means
the given tables and the database agree on those tables. Anything else is drift on that set:
a missing table, a missing column, a `DROP` for a column the database has and the table does
not. Drift on columns runs both ways, and a gate watching one direction would pass the other
half of those mistakes.

**`unmappedTables` compares the catalog.** It reads `information_schema.tables` over JDBC —
not through Exposed, which is the point — and returns qualified names of user tables that no
supplied `Table` declares. That is the third side: a whole table the database still has after
its Kotlin declaration is gone, in any user schema.

**`platform.flyway_schema_history` is not drift.** Flyway owns that table; no Kotlin `Table`
ever will. The exclusion is that one qualified name, not the `platform` schema: a leftover
table a platform migration created and later stopped describing must still fail. An empty
leftover schema, with no table inside it, is outside this check — this compares tables, not
schemas.

**`SchemaDrift` lives in `platform:persistence`'s `testFixtures`,** constructed with the
database and asked `from(vararg tables)` and `unmappedTables(vararg tables)`. A first draft
was an `object` with one function taking everything as parameters — a `Utils` class under
another name, and the exact shape `no-stateful-objects` forbids in `main`. That rule runs on
the production scope only, which is the sole reason it did not fire; a test fixture is still
code, and its quality does not depend on which source set holds it.

**The run over every table belongs to the composition root**, and lands in slice 13 with
`app`. This is forced, not preferred: "unmapped" is only meaningful against a complete set of
declared tables, so a partial list makes a neighbour's table look like something to drop —
and the only project allowed to see every module's tables is `app`, since `platform:*` may
not depend on `modules:*` at all (§4.4). That run calls both methods: `from` for the tables
it found, `unmappedTables` for everything the catalog has that those objects do not name.

**Discovery will be by scanning the classpath, not a registry,** when it is built. A registry
is a list a table can be left out of, and a table missing from it is a table the gate never
checks. Scanning arrives with its consumer in slice 13 rather than now, so the library it
needs is not taken before anything uses it.

## Consequences

Three directions are proven: a column Kotlin declares and the database lacks, a column the
database has and Kotlin does not, a table absent entirely, a whole table the database has
and no Kotlin table declares (same schema, another schema, and `platform` other than Flyway's
history), and a matching pair reporting nothing. They discriminate each other — if either
method always returned empty, several of them would fail.

One case exists for slice 13's sake: both methods are run against a migrated database, which
carries `platform.flyway_schema_history` that no Kotlin table declares, and report nothing.
Without that case the future full run could have failed on Flyway's own bookkeeping.

Tables used to prove the gate are declared in the test source set and created by hand. None
of them belongs in `db/migration`, where a table existing only to prove a check would ship to
production.

Until slice 13 there is a verified mechanism with no consumer. That is a real cost and worth
naming rather than glossing: it is the same shape as `PostgresFixture.migrated()` being built
a slice before anything needed it. The difference is that there is no earlier home for the
full run — before `app` exists, nowhere in the build may see all the tables.

On PostgreSQL, sequences created by hand rather than by a registered column are outside these
checks.

## Alternatives considered

**Each module checks its own schema.** Attribution would be local, and a module would stay
movable as a unit. Rejected because "unmapped" needs the complete set: each module would see
its neighbours' tables as objects to drop, and scoping the check to a module's own schema
would leave an orphaned schema — from a capability that was deleted — unnoticed by every
check there is. `unmappedTables` now reports leftover *tables* in any user schema; an empty
leftover schema is still outside, and remains a slice-13 question if it ever needs one.

**Folding the catalog comparison into `from`.** One call would report everything. Rejected
because the two answers come from different places — Exposed for the supplied tables, JDBC
metadata for the rest — and `from`'s existing cases, including the Flyway one, would start
failing the moment the catalog walk was added without an exclusion. The composition root
combines them.

**`arch-tests` with a new `integrationTest` source set.** Gates would live together. Rejected
because it changes what `arch-tests` is: today it reads source through Konsist, and this would
have it compile against every module and require Docker. ADR-057 already placed
database-dependent tests in `integrationTest` source sets, opt-in locally, and putting one
inside `arch-tests:test` would undo that.

**A registry of tables each module appends to.** Rejected: a table missing from the list is a
table the gate silently does not check — the failure this repository keeps refusing.

**Deferring the whole slice to 13.** Rejected because the mechanism can be proven now, and
proving it now means slice 13 wires up something already known to work rather than debugging
two things at once. Deferring only the catalog walk was considered after a review pointed at
`from`; rejected for the same reason — the planned classpath scan does not close a hole
inside `from`.
