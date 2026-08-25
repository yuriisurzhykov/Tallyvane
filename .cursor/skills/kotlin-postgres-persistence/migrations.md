# Migrations

## Applied by a command, never at startup

Startup migration is the default in most tutorials and it is the wrong default. It couples a
schema change to a process restart; it races the day there are two instances; and it leaves
readiness with nothing to verify, because the probe would be reporting on work it had just
performed itself.

So: a one-shot command the deploy runs, and the application starts only if it exited zero.
The port has two operations for the two roles:

```kotlin
interface Migrations {
    fun apply(): Applied          // the deploy
    fun pending(): List<String>   // readiness verifies
}
```

Blocking and not `suspend`: applying happens once from a command, and a readiness check
already tolerates a blocking call if it is bounded.

Report the version even when nothing was applied. Flyway leaves `targetSchemaVersion` null on
a no-op run, so a naive mapping prints "none" — which in a deploy log reads as "there is no
schema" when it means "already up to date". Fall back to `initialSchemaVersion`.

## Layout: one location, walked

Give Flyway a single location and let it walk:

```
src/main/resources/db/migration/<module>/V20260825020000__what_it_does.sql
```

`classpath:db/migration` finds files nested one directory deeper — verify that on the version
in use rather than assuming it, since it is a claim about a library.

The alternative, an explicit list of locations passed from the composition root, is visible in
a diff, which is a real argument. It is still a second registration step beside the build's
module list, and forgetting it fails at runtime with a missing table rather than at build time.

**Version numbers are timestamps, not per-module counters.** Two modules each starting at
`V1__` are a duplicate-version error. More importantly, ordering must be global: if a cross-
schema foreign key is allowed, the referenced table must exist first, and module-by-module
iteration orders those two by whatever order modules happen to be visited.

## Who creates schemas

With a schema per module, name exactly one schema to the tool — the one holding its history
table — and let each module's first migration create the schema it owns. Listing every schema
centrally is a second place to forget a capability.

Flyway specifics: `defaultSchema` is where `flyway_schema_history` goes; `schemas` names the
schemas it manages, the first being the default when `defaultSchema` is unset; `createSchemas`
lets it create them. Naming one schema and leaving `createSchemas` on is enough. Avoid
`initSql` — it is deprecated in favour of an `afterConnect` callback, which is a class to
write and register for one `create schema`.

Put the history table in a named schema rather than `public`, so bookkeeping is obviously not
application data.

## Case-insensitive text

Reach for a non-deterministic ICU collation before reaching for `citext`:

```sql
create collation platform.case_insensitive (
    provider = icu, locale = 'und-u-ks-level2', deterministic = false
);
-- then: email text collate platform.case_insensitive
```

An extension's comparison operators live in the schema it was installed into, and if that
schema is off `search_path` when a query runs, PostgreSQL silently falls back to
case-sensitive `text`. A collation is bound to the column at DDL time, reads nothing at query
time, and survives `CREATE DATABASE … TEMPLATE` — where a database-level `search_path` setting
does not.

Equality and uniqueness both work: a unique index on such a column refuses two values
differing only by case, which is exactly what an email column needs. `LIKE` is refused on a
non-deterministic collation; use `lower(col) like lower(…)` over an expression index.

`citext` in `public` also works, since `public` is on every database's default `search_path`.
It keeps `LIKE`, and it keeps the guarantee resting on session state that a later change can
remove without a word.

## Locks: how a migration takes an application down

Measured on PostgreSQL 17, and this is the trap worth knowing before the first large table.

`ALTER TABLE` needs `ACCESS EXCLUSIVE`, which conflicts with everything — including the
`ACCESS SHARE` any reader holds. When it cannot get the lock it queues, and **the queue is
ordered**: requests arriving after it wait behind it, even ones that would have been compatible
with the transaction actually holding the table.

```
=== ALTER TABLE behind an open reader, without lock_timeout
  the ALTER: sqlState=57014 ERROR: canceling statement due to statement timeout
  a plain SELECT arriving after it: sqlState=57014 ERROR: canceling statement due to statement timeout

=== ALTER TABLE behind an open reader, with lock_timeout=300ms
  the ALTER: sqlState=55P03 ERROR: canceling statement due to lock timeout
  a plain SELECT arriving after it: succeeded
```

So one long-running query plus one `ALTER TABLE` equals an outage, and the outage lasts as long
as the DDL is willing to wait.

**Every migration sets `lock_timeout`.** A migration that fails is recoverable — rerun it when
the long query is gone. An application stalled behind a lock queue is a customer-visible
incident. Prefer failing fast, and retry the migration rather than the outage.

Two smaller rules from the same source: adding a column with a default no longer rewrites the
table (PostgreSQL 11+), so it is cheap; adding `NOT NULL` or a check constraint is done in two
steps — `ADD CONSTRAINT … NOT VALID`, then `VALIDATE CONSTRAINT` — so the long scan takes a
weaker lock than the declaration does.

## `CREATE INDEX CONCURRENTLY` does not belong in a migration

Measured both ways, and neither works.

Inside a transaction, Flyway refuses the migration outright: *"Detected both transactional and
non-transactional statements within the same migration."*

With `executeInTransaction=false`, the statement is accepted and then waits forever. What it
waits on is visible in `pg_stat_activity`:

```
pid 65 | idle in transaction | ClientRead | SELECT COUNT(*) FROM pg_namespace WHERE nspname=$1
pid 66 | active              | Lock/virtualxid | create index concurrently ... on widgets
```

`CREATE INDEX CONCURRENTLY` waits for every transaction that could see the table to finish, and
pid 65 is *Flyway's own connection*, sitting idle inside a transaction. The migration deadlocks
against the tool running it.

So concurrent index creation is an operational step run outside the migration tool, before or
after the deploy, with its own `statement_timeout` and its own record of having happened. Note
also that a failed `CREATE INDEX CONCURRENTLY` leaves an invalid index behind, which must be
dropped before retrying — one more reason it does not belong in an automated migration.

## Server-side timeouts

Set these on the role or the database, not per call site — a coroutine timeout cannot interrupt
a blocked JDBC call, and a call site can be forgotten:

- `statement_timeout` — the general bound; without it a blocked writer waits indefinitely.
- `lock_timeout` — for migrations especially, per above.
- `idle_in_transaction_session_timeout` — the state that held the concurrent index build
  hostage above is the same state that stops vacuum reclaiming rows in production.

## Changing a schema that has shipped

**A merged migration is never edited.** Its checksum is recorded in databases that already
applied it. The exception is a migration that has never been applied to any database that
outlives a test container — then editing is safer than shipping a first migration plus an
immediate undo.

**Destructive change is split across releases**: add the new, switch the code, drop the old.
Each step is deployable alone and each is reversible without data loss.

**Repeatable migrations** (`R__`) suit views, functions and indexes, which must therefore be
idempotent. They run after versioned ones, and only when their checksum changes.

**`baselineOnMigrate`** adopts an existing schema as version 1. It is convenient and it is how
a misconfigured deploy silently declares an unmigrated production database "already at
version 1". Prefer an explicit, one-time baseline.

## Extension privileges

Since PostgreSQL 13 some extensions are *trusted* — `citext`, `hstore`, `pgcrypto`, `pg_trgm`,
`uuid-ossp` among them — and installable by a non-superuser holding `CREATE` on the database.
Untrusted ones still need a superuser, which means a manual step on the server and a
migration that cannot run as the application's role. Check the extension's page before
planning around it.

## What a machine can check

Migrations are SQL, so Kotlin gates do not read them. Two checks are worth writing yourself:

**Own-schema-only.** A migration may name only the schema it owns. A `create view` joining a
neighbour's tables otherwise passes every check a Kotlin-only gate has, while creating exactly
the coupling schema-per-module exists to prevent. Allow a foreign key to cross a schema and
forbid a query to — `references identity.users (id)` and `join jobs.companies` can sit in one
file and only the second is a violation. Anchor the patterns to positions where a schema can
actually stand (`from`, `join`, `table`, `view`, `index … on`), or table aliases will be
reported as schemas.

**Schema drift**, in three directions. See [SKILL.md](SKILL.md) and note that
`MigrationUtils.statementsRequiredForDatabaseMigration` is the function that includes `DROP`
for columns of the tables it is given; the `SchemaUtils` equivalent is deprecated and
one-directional. It does not enumerate the catalog, so a leftover table with no Kotlin
declaration needs a separate catalog comparison. "Unmapped" is only meaningful against the
complete set of declared tables, so the full comparison belongs wherever every table is
visible — usually the composition root — and discovery should scan rather than read a
registry a table can be left out of.
