---
name: tallyvane-persistence
description: >-
  What this repository decided about persistence — the transaction port and its Exposed
  adapter, pool numbers and their source, migration layout, case-insensitive text, and how
  database tests get isolated databases. Use when touching platform:persistence, writing a
  migration, adding a database-backed test, or before proposing a change to any of these,
  since each is a recorded decision with an ADR behind it.
---

# Persistence in this repository

General practice for this stack — including the traps that fail silently — lives beside this
one in [kotlin-postgres-persistence](../kotlin-postgres-persistence/SKILL.md). That skill is
written without reference to this repository, so it can be lifted into a personal skills
directory or another project unchanged; it lives here because here is where it is used, and
because a document whose claims are measurements deserves review and version history.

This file records only what *this* repository decided, and where the reasoning is written
down, so a proposal can be checked against it before being argued.

## Decided, with the record

| Decision | Where |
|---|---|
| Exposed, DSL only; no DAO | ADR-049 |
| Migrations in the module, timestamp versions, applied by their own command | ADR-051 |
| A transactional block states a `Verdict`; it does not commit by returning | ADR-052 |
| A port with two implementations has one suite both must pass | ADR-046 |
| Transactions over JDBC, on a dispatcher bounded by the pool | ADR-058 |
| Flyway creates one schema; migrations create the rest; a database per test | ADR-059 |
| Schema drift is a build failure, detected in three directions | ADR-060 |

`docs/adr/` holds them. Read the ADR before proposing a change to any row — each lists what was
rejected and why, and most of those rejections were measured rather than reasoned.

## Shapes to follow

`Persistence` is the port; `PostgresPersistence` implements it and is the only name in the
module that says "Hikari and Exposed". `AutoCloseable` is on the implementation, not the port:
whoever built the pool closes it, and a consumer that merely runs transactions must not be able
to shut it down.

`Migrations` has `apply` for the deploy and `pending` for readiness, because the deploy applies
and the probe verifies.

`DatabaseAccess` — url, user, password — lives in `main`, not in `testFixtures`, so production
and tests share one vocabulary for reaching a database instead of two that drift.

Test databases come from `PostgresFixture.empty()` or `.migrated()`, one per test. There is no
shared database and no truncation.

## Numbers, and where they come from

Pool size is **8**, fixed by the memory budget in `ops/README.md` rather than chosen here, and
the adapter's dispatcher parallelism follows it so the two cannot drift.

The rest are derived from §1.5's p95 targets: `connectionTimeout` 2 s, `validationTimeout` 1 s,
`keepaliveTime` 2 min, `maxLifetime` 30 min, pgjdbc `connectTimeout` 5 s and `socketTimeout`
30 s, Exposed `defaultQueryTimeout` 10 s.

§1.5 also requires a hundred concurrent requests at those targets, which with eight connections
queues at the pool — and §1.5 says that is settled by a load test on seeded data, not by
reasoning. Quote measured numbers here, never remembered ones.

## Case-insensitive text is a collation, not `citext`

`platform.case_insensitive`, created by the first platform migration; columns declare
`text collate platform.case_insensitive`. `citext` was tried first and removed: its guarantee
depends on `search_path`, and a database-level `search_path` does not survive the
`CREATE DATABASE … TEMPLATE` every test database is made with. ADR-051 was amended and ADR-059
carries the measurement.

`LIKE` is refused on that collation. Prefix search is `lower(col) like lower(…)`.

## Before writing a migration

It goes in `db/migration/<module>/`, named `V<timestamp>__<what>.sql`, and it may name only its
own schema — `MigrationSchemaSpec` enforces that, and a foreign key crossing a schema is allowed
where a query is not.

The `platform` schema is created by Flyway, not by a migration, because its history table lives
there and must exist first.

It sets `lock_timeout`. Measured in `playground/ddl-locks/`: an `ALTER TABLE` queued behind one
open reader also kills a plain `SELECT` that arrives after it, because the lock queue is ordered.
A failed migration is rerunnable; an application stalled behind that queue is an incident.
`CREATE INDEX CONCURRENTLY` is not written as a migration at all — the same spike shows it
waiting on Flyway's own idle-in-transaction connection, indefinitely.

A merged migration is not edited. The one exception applies only while no database outside a
test container has ever applied it, and taking it requires saying so out loud.

## Running things

```bash
./gradlew check                                   # unit tests, lint, graph, Konsist
./gradlew integrationTest                         # needs Docker; opt-in locally, its own CI step
./gradlew :migrate:installDist                    # the deploy artefact for migrations
./gradlew :playground:transactions:run            # commit/rollback/nesting by hand, against your own Postgres
./gradlew :playground:isolation:run               # what READ COMMITTED allows; when a retry is correct
./gradlew :playground:ddl-locks:run               # what a migration does to readers
./gradlew :playground:timeout-bounds:run          # which timeout actually stops a blocked statement
```

Nothing calls `:migrate` yet, and `PostgresPersistence` is closed by nobody, because `app` does
not exist. Both are recorded debts against slice 13 in `backend/.plans/`.

## Timeouts, and the one that was not there

`statement_timeout` 15 s, `lock_timeout` 3 s, `idle_in_transaction_session_timeout` 60 s, carried
on the connection through pgjdbc's `options` property by `SessionTimeouts` — not on the role and
not on the database, because a database-level setting does not survive the
`CREATE DATABASE … TEMPLATE` every test database is made with (ADR-059). Flyway gets `lock_timeout`
and nothing else: a migration is meant to hold a long transaction. ADR-061 has the reasoning.

`addDataSourceProperty` is called only by `DriverProperties`, and `no-raw-datasource-property`
enforces that. A non-`String` value there is accepted by HikariCP and silently ignored by pgjdbc,
which is not hypothetical: `socketTimeout` and `connectTimeout` shipped as `Int` constants and
neither was in effect. `playground/timeout-bounds/README.md` has the measurement.
