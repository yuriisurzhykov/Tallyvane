# ADR-059. Flyway creates one schema, migrations create the rest, and each test gets its own database

## Status

Accepted. Implements the layout ADR-051 decided; settles what ADR-051 left open.

## Context

ADR-051 fixed the layout — migrations as resources under `db/migration/<module>/`, one
location handed to Flyway, timestamp versions, applied by a one-shot command — and left
three things open that only appear when the thing is built.

**Where Flyway's own bookkeeping lives.** Flyway keeps a `flyway_schema_history` table
recording which migrations ran and with what checksum. It goes in the "default schema":
`defaultSchema` if set, otherwise the first entry of `schemas`, otherwise the connection's
default. With a schema per capability there is no obvious default.

**Who creates the schemas.** Flyway can create the schemas listed in `schemas`. Listing
every capability's schema there would be the "second place to forget" ADR-051 rejected for
locations.

**How tests get a known starting state.** Slice 6 decided a database per spec cloned from a
migrated template, and left it to be built here.

One fact discovered while building, which changes what the first migration must contain:
an extension's operators live in the schema it was installed into, and if that schema is
not on `search_path` when a query runs, PostgreSQL does not find them and compares as
ordinary case-sensitive text. No error, no warning. `citext` would simply stop being
case-insensitive, and the symptom would be a person unable to sign in with their own
address in a different case.

## Decision

**`platform` holds Flyway's history table, and it is the only schema Flyway is told
about.** `schemas` and `defaultSchema` both name it; `createSchemas` is on, so Flyway
creates that one schema and no other. Every capability's schema is created by that
capability's own first migration, so adding a capability still registers nothing anywhere.

`platform` rather than a name of Flyway's own — `flyway`, say — because
`MigrationSchemaSpec` permits a migration under `db/migration/platform/` to name exactly
that schema, and the platform module owns it by the same convention every capability
follows. One name instead of two.

**Flyway creates that schema itself, rather than being given `initSql`.** `initSql` is
deprecated in favour of an `afterConnect` callback, which is a class to write and register
for one `create schema`; letting Flyway create the one schema it needs asks for neither.
Found by running the command and reading its output, not from the documentation.

**The first platform migration installs `citext` and sets `search_path` on the database.**
Setting it on the database rather than per connection covers every session — the
application, `psql`, and Flyway's own later runs. `current_database()` through a `DO` block,
because `ALTER DATABASE` takes a name and not an expression.

**Each integration test gets its own database.** `PostgresFixture.empty()` for tests about
migrating, `PostgresFixture.migrated()` for everything that needs the schema, cloned from a
template migrated once per JVM. `create database … template …` copies files instead of
replaying migrations, which is fast enough per test.

**`Migrations` is a port with two operations,** `apply` and `pending`, because ADR-051 gives
the two roles to different callers: the deploy applies, and readiness verifies. Blocking and
not `suspend` — applying happens once from a command, and `pending` is read by a
`HealthCheck`, which already documents that a check may block and is bounded by a decorator
(ADR-058).

**The command is its own module, `backend/migrate`,** with the `application` plugin, reading
`TALLYVANE_DB_URL`, `TALLYVANE_DB_USER` and `TALLYVANE_DB_PASSWORD`. Outside `modules.yaml`
for the same reason `app` and `arch-tests` are.

## Consequences

Truncation is gone from the test suite. It needed a list of tables, and a table added later
could be left out of that list silently — the failure mode this repository keeps refusing.
The isolation is asserted rather than trusted: `PostgresFixtureSpec` proves two callers get
different databases and that one's tables are invisible to the other, because every other
integration spec now rests on that claim.

It was not a hypothetical problem. `FlywayMigrationsSpec` sets `search_path` **on the
database**, so on a shared database it would have silently changed the environment every
other spec ran in.

`citext` is covered in both directions: a comparison in a different case finds the row, and
with `platform` taken off `search_path` the type does not resolve at all — which proves the
migration's third statement is load-bearing rather than decoration.

ADR-051 asked for one claim to be verified where it first runs: that Flyway walks the
per-module directories under a single location. It does — the location is
`classpath:db/migration` and the file sits one directory deeper, under `platform/`.

Flyway brings Jackson as a non-optional dependency, so a second JSON library now sits beside
kotlinx-serialization. Nothing depends on it; it is recorded so its arrival is not a surprise
later.

`citext` is a trusted extension from PostgreSQL 13, so the application's own role installs it
given `CREATE` on the database. No superuser step on the server.

Nothing calls the command yet: there is no compose file, no deploy script and no CI job that
runs it. ADR-051's ordering — deploy applies, then starts the application only on success —
is therefore unenforced, and slice 13 owns both the ordering and its test.

## Alternatives considered

**Listing every capability's schema in `schemas`.** Flyway would create them all, and adding
a capability would mean editing a central list next to `settings.gradle.kts` and
`modules.yaml` — rejected by name in ADR-051.

**The history table in `public`.** Mixes Flyway's bookkeeping with whatever else lands in the
default schema, and makes it unobvious that the table is not application data.

**A schema of Flyway's own, `flyway`.** One more name for no gain, and `MigrationSchemaSpec`
would have had nothing to say about a migration that created it.

**`citext` in `public`.** Found by default because `public` is on `search_path` already.
Rejected in favour of the platform schema, which keeps the extension where the module that
requires it lives; the cost is that `search_path` must be set deliberately, which the
migration does and a test pins.

**Truncating tables between specs.** Cheaper to write, and needs a list of tables that a new
table can be left out of.

**One database per spec rather than per test.** Fewer databases, and the conformance suite's
`fresh()` is per test — a shared database within a spec would put truncation back for the
cases that write.
