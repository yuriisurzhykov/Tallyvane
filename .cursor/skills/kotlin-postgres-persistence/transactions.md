# Transaction boundaries and the pool

## The boundary is a port, and the block states a verdict

A transaction boundary that commits by returning and rolls back by throwing forces callers
to express "no, and here is why" as an exception — which turns an ordinary business refusal
into a failure. Have the block return a verdict instead:

```kotlin
sealed interface Verdict<out T> {
    val value: T
    data class Commit<out T>(override val value: T) : Verdict<T>
    data class Rollback<out T>(override val value: T) : Verdict<T>
}

interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> Verdict<T>): T
}
```

A rollback still answers: `inTransaction { Verdict.Rollback("email already taken") }` returns
the reason and leaves no rows. Callers never see `Verdict` — it exists inside the block only,
which is worth enforcing if the project has a mechanism for that.

## Blocking, and where it is allowed to happen

JDBC's interface cannot be non-blocking: it returns a result, and a result cannot be returned
before it arrives. Exposed's `suspendTransaction` from `exposed-jdbc` exists, in JetBrains'
own words, "for compatibility with JDBC drivers, to call suspend functions alongside blocking
database operations". The SQL underneath still occupies its thread.

Two consequences.

Say it in the port's documentation. A signature that reads as a promise it does not keep is
the most expensive kind of comment-free code.

Give the adapter its own dispatcher, sized to the pool:

```kotlin
private val blocking = Dispatchers.IO.limitedParallelism(POOL_SIZE)
```

Bare `Dispatchers.IO` has 64 threads by default. With a pool of eight and a hundred
concurrent requests, ninety-two calls wait inside `getConnection`, occupying up to
sixty-four *shared* IO threads — and everything else that needs IO starves. Tying the
dispatcher's parallelism to the pool's size in one class also stops the two numbers drifting
apart.

The queue does not disappear: a hundred requests over eight connections still queue. What
changes is that they queue as suspended coroutines at the pool boundary rather than as
captured threads.

## R2DBC, and when it is worth it

R2DBC is genuinely non-blocking: the driver registers interest in a socket and the coroutine
suspends, costing no thread. Consider it only with these facts in hand.

Flyway has no R2DBC support and requires a JDBC URL, so a project with migrations carries the
JDBC driver regardless — R2DBC is then a *second* driver, not a replacement.

The gain is threads and memory, not throughput: PostgreSQL serves a bounded number of
concurrent queries either way, so the pool remains the limit.

A blocked JDBC call is legible in a thread dump. An event-loop path is not.

On JDK 24+ virtual threads remove most of the cost of blocking (JEP 491 ended pinning inside
`synchronized`); before 24 that pinning is still possible, and pgjdbc holds a `synchronized`
block in its statement cache. If the toolchain is 24 or later, virtual threads are the
cheaper answer than R2DBC.

## Nesting

Decide whether nesting is allowed, and enforce the decision.

Exposed joins a nested block to its parent by default (`useNestedTransactions = false`), so a
rollback inside undoes the outer's writes as well. Setting it to `true` gives independence
through savepoints.

If the port forbids nesting — a defensible choice, since two writes atomic enough to share a
transaction belong to one use case — then refuse explicitly:

```kotlin
check(TransactionManager.currentOrNull() == null) { "A transaction is already open." }
```

That check holds across a multi-threaded dispatcher: Exposed 1.x keeps the transaction in the
coroutine context, so it survives a `withContext` hop. Verify this on the version in use
rather than trusting it — a nested call reporting the parent's transaction id is the
observable symptom.

## Retries

Exposed re-runs the whole block on `SQLException`, governed by `maxAttempts` /
`defaultMaxAttempts`. A block that decides and then writes must not be re-run silently: set
it to 1, and write any retry explicitly where a reader can see it.

## `Database.connect` is not a constructor-safe call

It registers the database with the global `TransactionManager` and makes it the default for
any `transaction { }` without an explicit `db`. So:

- do not call it while constructing an object — construction would change, invisibly, where
  unqualified transactions elsewhere go;
- always pass `db` explicitly;
- in tests, where each case may have its own database, both rules matter at once.

## Pool numbers

Derive them; do not copy them.

**Size** comes from the memory budget and the database's core count, not from a tutorial.
PostgreSQL serves a bounded number of concurrent queries, so a larger pool buys queueing
inside the database instead of outside it. HikariCP's own wiki argues for small pools with
measurements.

**`connectionTimeout`** chooses between "slow" and "failed" under a burst. A request that
waited two seconds has missed a 400 ms p95 but was served; failing at 400 ms turns a brief
queue into errors.

**Driver timeouts are mandatory, not tuning.** A coroutine timeout cannot interrupt a
blocking socket read, so `socketTimeout` and `connectTimeout` on pgjdbc are what actually
bound a hung connection. Add a per-statement bound as well (`queryTimeout`), below
`socketTimeout`, so a slow query and a dead socket are distinguishable.

**`keepaliveTime`** matters wherever a NAT or tunnel silently drops idle connections: it
finds a dead connection before a request does.

**`maxLifetime`** retires connections so a long-lived process does not accumulate stale ones;
keep it below any server-side idle timeout.

Omit `connectionTestQuery` with a modern JDBC4 driver — Hikari uses `isValid()` and the query
only adds a round trip.

## Lifetime

Whoever builds the pool closes it, which means the composition root. Put `AutoCloseable` on
the implementation and not on the port: a consumer that merely runs transactions has no
business shutting down a pool other code is using.
