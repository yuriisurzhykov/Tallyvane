---
name: kotlin-postgres-persistence
description: >-
  Rules for talking to PostgreSQL from a Kotlin backend — transaction boundaries over
  Exposed and JDBC, connection pool sizing, Flyway migrations, schema drift, and tests
  against a real database. Use when adding or reviewing persistence code, choosing a
  transaction API, writing or ordering migrations, sizing a pool, setting up
  database-backed tests, or when a database guarantee needs to be shown to hold rather
  than assumed.
---

# Kotlin persistence over PostgreSQL

Most published advice on this stack is confidently wrong in three specific ways, and each
one was disproved by measurement rather than argument. Start here:

| Common advice | What is actually true |
|---|---|
| Wrap queries in `newSuspendedTransaction(Dispatchers.IO)` for "non-blocking, coroutine-safe" access | The name is stale (`suspendTransaction` in Exposed 1.x) and the claim is false: JDBC blocks its thread. The wrapper only lets you *call* suspend functions inside |
| Run Flyway at application startup | Couples schema change to process restart, races between two instances, and leaves readiness with nothing to verify — it reports on work it just did |
| `maximumPoolSize = 10`, "start with 10–20" | A pool size is derived from a memory budget and the database's core count, and then measured under load. A number copied from a tutorial is a guess wearing a decimal point |

The rest of this skill is what those three corrections imply, plus the traps that no
tutorial mentions because they fail silently.

## Non-negotiables

1. **A `suspend` signature over JDBC does not mean non-blocking.** Say so in the port's
   own documentation, or the next reader will believe the signature.
2. **Blocking database work runs on a dispatcher whose parallelism equals the pool size.**
   Not bare `Dispatchers.IO`: it defaults to 64 threads, so a burst parks dozens of shared
   IO threads inside `getConnection` and starves unrelated work — health checks, HTTP
   clients, file reads. `Dispatchers.IO.limitedParallelism(poolSize)`, owned by the adapter,
   makes that impossible instead of unlikely.
3. **Migrations are applied by a one-shot command the deploy runs**, never by the
   application at startup. Readiness then *verifies* they are applied.
4. **Tests run against the same PostgreSQL image production runs.** Not H2 in
   compatibility mode: different dialect, different collation behaviour, no `create
   extension`. A green suite there is a confident claim with nothing behind it.
5. **Every test gets its own database.** Truncation between tests needs a list of tables
   that a table added later can be left out of, silently.
6. **Server-side timeouts are set on the role or the database**, not left to call sites:
   `statement_timeout`, `lock_timeout`, `idle_in_transaction_session_timeout`. A coroutine
   timeout cannot interrupt a blocked JDBC call, and `idle in transaction` is what holds
   both vacuum and concurrent index builds hostage.
7. **An invariant the database can express belongs in the schema.** A unique index, a check,
   a foreign key. A guard in Kotlin protects against nothing that arrives concurrently.
8. **Nothing that talks to the network runs inside a transaction.** Decide inside, record the
   intent, act afterwards from something that can retry — a transactional outbox.

## Traps that fail silently

These are the ones worth knowing before writing code, because none of them produces an
error.

**Nested transactions join their parent.** In Exposed, a nested block shares the outer
transaction by default, so a rollback inside discards the outer's writes too. Measured on
Exposed 1.4.0: a nested `suspendTransaction` reports the same transaction id as its parent.
If the port forbids nesting, check `TransactionManager.currentOrNull()` and refuse — and
that check is sound across dispatcher threads, because 1.x carries the transaction in the
coroutine context rather than a thread-local.

**Exposed retries a transaction block on `SQLException`.** `defaultMaxAttempts` re-runs the
*whole block*. If the block decides and then writes, a retry repeats everything else it did.
Set it to 1 — and then retry deliberately, only on `40001` and `40P01`, only where the block
is safe to re-run. A serialization failure is PostgreSQL's contract asking the loser to try
again, not a defect: measured, and the retry succeeded. See [concurrency.md](concurrency.md).

**Checking before inserting is a race.** Under `READ COMMITTED` two sessions both read "this
address is free" and both insert it — measured, two rows. Uniqueness is enforced by a unique
index, never by a preceding `select`.

**A conflicting insert waits rather than failing.** While the first session's insert is
uncommitted, the second blocks: no error until a `statement_timeout` cancels it with `57014`,
and only after the first commits does the honest `23505` arrive. So a slow transaction stalls
every writer competing for that key, and each of them holds a connection while waiting.

**A blocked `ALTER TABLE` takes unrelated reads with it.** Measured: behind one open reader,
an `ALTER` waits — and a plain `SELECT` arriving *after* it dies too, queued behind the DDL.
With `lock_timeout` the `ALTER` gives up quickly and reads keep flowing.

**`CREATE INDEX CONCURRENTLY` cannot be run from a Flyway migration.** Measured both ways:
inside a transaction Flyway refuses the migration outright; with `executeInTransaction=false`
the statement waits on `virtualxid` for Flyway's *own* connection, which sits idle in a
transaction — indefinitely. Concurrent index creation is a separate operational step.

**`Database.connect` mutates global state.** It registers the database with Exposed's
`TransactionManager` and makes it the default for any `transaction { }` without an explicit
`db`. Never call it in a constructor, and always name the database in every transaction.

**An extension's operators depend on `search_path`.** `citext` compares
case-insensitively only while the schema holding its operators is on `search_path`;
otherwise PostgreSQL falls back to case-sensitive `text` with no error. Prefer a
non-deterministic ICU collation on the column — it is bound to the column at DDL time and
cannot stop applying. The cost is that `LIKE` is refused on such a column; use
`lower(col) like lower(…)` over an expression index.

**Database-level settings do not survive `CREATE DATABASE … TEMPLATE`.** `ALTER DATABASE …
SET search_path` is keyed to the database rather than stored in it, so a clone gets the
default. Any guarantee that rests on a database-level setting is absent in every cloned
test database — and if tests only assert against freshly migrated databases, they agree
with themselves and notice nothing.

**Schema drift runs in two directions.** A gate that reports only what the database is
missing passes every "column exists in the database and in no Kotlin table" mistake. In
Exposed, `SchemaUtils.statementsRequiredToActualizeScheme` is deprecated and one-directional;
`MigrationUtils.statementsRequiredForDatabaseMigration` (in `exposed-migration-jdbc`)
includes `DROP`.

## Deeper material

- Transaction boundaries, the verdict-carrying block, and pool numbers:
  [transactions.md](transactions.md)
- Races the default isolation level allows, serialization failures, and when to retry:
  [concurrency.md](concurrency.md)
- Migration layout, ordering, and the rules for changing a shipped schema:
  [migrations.md](migrations.md)
- Databases in tests, isolation, and what a database test can and cannot prove:
  [testing.md](testing.md)

## Reviewing persistence code

Ask these in order. Each has caught a real defect.

1. Does any `suspend` signature imply non-blocking work that blocks?
2. Can blocking database work consume more threads than there are connections?
3. Would a nested call to the transaction boundary be noticed, or silently joined?
4. Can a block be re-executed by the library without the author's knowledge?
5. Does any guarantee rest on session state — `search_path`, a default database, a
   thread-local — that a clone, a pool, or another thread can lose?
6. Is the schema comparison one-directional?
7. Does a test observe its result through the layer it is testing?
8. Would each test still pass if run alone, and in a different order?

## Primary sources

Prefer these over tutorials, in this order: PostgreSQL's own documentation for anything
about the database; Exposed's API reference for what a function actually does, including its
deprecations; Flyway's configuration reference; HikariCP's pool-sizing wiki. When a claim
about a library matters, run it against a real container and record the output — the three
corrections at the top of this file all came from doing that, not from reading.
