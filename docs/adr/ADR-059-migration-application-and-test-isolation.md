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

Two facts discovered while building, which together changed what the first migration
contains.

An extension's operators live in the schema it was installed into, and if that schema is
not on `search_path` when a query runs, PostgreSQL does not find them and compares as
ordinary case-sensitive text. No error, no warning. `citext` would simply stop being
case-insensitive, and the symptom would be a person unable to sign in with their own
address in a different case.

And the mechanism that was supposed to prevent that — setting `search_path` on the
database — **does not survive cloning**. Measured: a database created with `create
database … template …` reported `search_path` as `"$user", public` and could not resolve
the type at all. Database-level settings are keyed to the database, not stored in it, so
they are not copied. Every integration database is a clone, so the guarantee would have
been absent exactly where it was being tested, and the suite would have agreed with
itself: the only case using a cloned database asserted that a schema existed.

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

**Case-insensitivity is an ICU collation on the column, not the `citext` extension.** The
first platform migration creates `platform.case_insensitive` — provider `icu`, locale
`und-u-ks-level2`, `deterministic = false` — and columns declare it qualified:
`email text collate platform.case_insensitive`. This supersedes ADR-051's naming of
`create extension if not exists citext` as that migration's content.

A collation is bound to the column when the table is created and stored with it. It reads
nothing at query time and travels with the schema when a database is cloned, so unlike an
extension's operators it cannot silently stop applying. Measured on `postgres:17-alpine`:
equality ignores case, and a unique index on such a column refuses two rows differing only
by case, which is what an email column actually needs.

One limitation, recorded so it is known rather than discovered: PostgreSQL refuses `LIKE`
on a non-deterministic collation. Case-insensitive prefix search is `lower(email) like
lower(…)`, over an expression index where it matters.

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

The case-insensitivity guarantee is asserted on a **cloned** database as well as a freshly
migrated one, which is the case the first attempt got wrong, plus a case proving a unique
index refuses two addresses differing only by case. Without the clone case the suite would
again only agree with itself.

ADR-051 asked for one claim to be verified where it first runs: that Flyway walks the
per-module directories under a single location. It does — the location is
`classpath:db/migration` and the file sits one directory deeper, under `platform/`.

Flyway brings Jackson as a non-optional dependency, so a second JSON library now sits beside
kotlinx-serialization. Nothing depends on it; it is recorded so its arrival is not a surprise
later.

No extension is installed at all, so the question of who may install one does not arise.
`citext` would have been installable by the application's own role — it is trusted from
PostgreSQL 13 — but a collation needs no privilege beyond creating objects in a schema the
role already owns.

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

**`citext` in the platform schema, with `search_path` set on the database.** The first
implementation. Rejected on measurement: the setting is not copied by `create database …
template …`, so every integration database lost it silently.

**`citext` in `public`.** This does work — `public` is on the default `search_path` in every
database, clones included — and it keeps `LIKE`. Rejected because the guarantee still rests
on session state: a future `search_path` without `public` reintroduces silent
case-sensitivity, and the failure mode is a person unable to sign in. A collation removes
the possibility rather than making it unlikely. The price is `lower()` in a prefix search,
which is a visible inconvenience rather than an invisible defect.

**`search_path` at role level instead of database level.** Survives cloning, and couples the
guarantee to a role name that the migration runner and the application need not share.

**Truncating tables between specs.** Cheaper to write, and needs a list of tables that a new
table can be left out of.

**One database per spec rather than per test.** Fewer databases, and the conformance suite's
`fresh()` is per test — a shared database within a spec would put truncation back for the
cases that write.
