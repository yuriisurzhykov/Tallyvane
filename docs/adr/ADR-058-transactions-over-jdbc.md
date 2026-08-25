# ADR-058. Transactions run over JDBC, on a dispatcher bounded by the pool

## Status

Accepted.

## Context

`TransactionRunner` is a `suspend` port in `platform:kernel` (ADR-052). Slice 7
implements it, and Exposed 1.x offers two paths. Four facts settled the shape, none of
them assumed.

`suspendTransaction` from `exposed-jdbc` exists, in JetBrains' own documentation, "for
compatibility with JDBC drivers, to call suspend functions alongside blocking database
operations". The SQL under it still blocks its thread, because JDBC's interface cannot
do otherwise: it returns a result, and a result cannot be returned before it arrives.
The same page says plainly that `transaction()` "executes synchronously on the current
thread" and "might block other parts of your application if not managed carefully".

Flyway has no R2DBC support and requires a JDBC URL — verified, not recalled. Migrations
are slice 8, so pgjdbc stays in this project whatever is chosen here; R2DBC would be a
second driver beside it, not a replacement.

Exposed joins a nested transaction to its parent by default, so an inner rollback
discards the outer's writes. Measured with a probe against a real container: a nested
`suspendTransaction` reported the same transaction id as its parent. The same probe
showed `TransactionManager.currentOrNull()` is empty outside a transaction, populated
inside, and still populated after a `withContext(Dispatchers.IO)` hop — Exposed 1.x
carries the transaction in the coroutine context, not a thread-local.

Exposed re-runs the whole block on `SQLException` by default (`maxAttempts`,
`defaultMaxAttempts`). This port's block decides and then writes, so re-running it
repeats everything else it did.

## Decision

**JDBC with HikariCP.** R2DBC is rejected below.

**Blocking work runs only on a dispatcher whose parallelism equals the pool size.**
`Dispatchers.IO.limitedParallelism(8)`. Without that bound, a burst of transactions
would wait inside `getConnection` on shared IO threads and starve unrelated IO of them —
the same failure ADR-054 fixed one layer up, where a health check on a shared dispatcher
could stall everything else.

**`suspend` in the port means "callable from a coroutine", not "costs no thread",** and
the adapter's KDoc says so. A signature that reads as a promise it does not keep is what
made the health-check bound decorative for a whole evening.

**Nesting is refused**, not joined: `inTransaction` checks
`TransactionManager.currentOrNull()` and throws. Sound on a multi-threaded dispatcher
because the transaction lives in the coroutine context, as measured above.

**`defaultMaxAttempts = 1`.** A retry that is wanted is written where a reader can see
it is a retry.

**Numbers.** Pool size 8, which `ops/README.md`'s memory budget fixes rather than leaves
open, and the dispatcher follows it so the two cannot drift. Hikari
`connectionTimeout` 2 s, `validationTimeout` 1 s, `keepaliveTime` 2 min,
`maxLifetime` 30 min. pgjdbc `connectTimeout` 5 s and `socketTimeout` 30 s — the driver
bounds ADR-054 makes mandatory, since a coroutine timeout cannot interrupt a blocking
socket read. Exposed `defaultQueryTimeout` 10 s as the bound on one statement, below
`socketTimeout` so a slow query and a dead socket are distinguishable.

**`PostgresPersistence` owns the pool, the Exposed database and the dispatcher**, and is
`AutoCloseable`. The composition root closes it, as with every lifetime here.

## Consequences

`TransactionRunnerConformance` now runs on the adapter as well as the fake, which is what
ADR-046 was for. It earned itself immediately: the nesting case is the one Exposed's
default would have failed silently, and removing the guard fails exactly that case and no
other — checked by removing it.

The pool, not the thread model, is the concurrency limit. §1.5 requires a hundred
concurrent requests at p95 200 ms read and 400 ms write; with eight connections those
requests queue, and whether the target holds depends on per-query time. §1.5 already says
that is settled by a load test on seeded data rather than by reasoning, and this decision
does not change it.

`connectionTimeout` of 2 s chooses "slow" over "failed" under a burst: a request that
waited two seconds has already missed p95 but is served, where failing at 400 ms would
turn a brief queue into errors.

Nothing closes the pool yet, because `app` does not exist. Slice 13 owns that.

## Alternatives considered

**R2DBC.** The port's `suspend` would stop over-promising, and no bounded dispatcher would
be needed. Rejected: Flyway keeps JDBC in the project regardless, so this adds a second
driver rather than replacing one; Exposed's R2DBC path arrived in 1.0 while its JDBC path
has years behind it, and slice 9's schema-drift gate leans on the older one; the gain is
threads and memory, not database capacity, because Postgres serves a bounded number of
concurrent queries either way; and a blocked JDBC call is legible in a thread dump where
an event-loop path is not.

**JDBC on virtual threads.** Would largely dissolve the cost of blocking. Rejected for
now on dates: pinning inside `synchronized` was removed by JEP 491 in **JDK 24**, and this
build's toolchain is 21. pgjdbc's own pinning regression (42.7.8) is fixed in 42.7.9 and
we pin 42.7.13, but the driver still holds a `synchronized` block in `LruCache`, which can
pin on a JDK older than 24. Worth revisiting when the toolchain moves.

**Leaving Exposed's retries on.** Rejected: silent re-execution of a block that does more
than SQL.

**`useNestedTransactions = true`.** Makes nesting independent through savepoints, which
contradicts a port that forbids nesting outright; the guard is the honest expression of
the contract.
