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

**Schema drift**, in both directions. See [SKILL.md](SKILL.md) and note that
`MigrationUtils.statementsRequiredForDatabaseMigration` is the function that includes `DROP`;
the `SchemaUtils` equivalent is deprecated and one-directional. "Unmapped" is only meaningful
against the complete set of declared tables, so the full comparison belongs wherever every
table is visible — usually the composition root — and discovery should scan rather than read a
registry a table can be left out of.
