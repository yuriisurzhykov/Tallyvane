# ADR-061. Timeouts are carried on the connection, as strings

## Status

Accepted. Built in `platform:persistence`, with a gate and an integration test.

## Context

ADR-058 gave the pool client-side bounds — pgjdbc `connectTimeout` 5 s and `socketTimeout` 30 s,
Exposed `defaultQueryTimeout` 10 s — and a comment in `PostgresPersistence` called them "what
actually stops a hung connection". Neither claim had been tested through the real pool. Two
measurements, both in `playground/timeout-bounds/`, changed the picture.

**The two driver bounds were not in effect at all.** They were passed as
`addDataSourceProperty("socketTimeout", 30)`, an `Int`. HikariCP keeps `dataSourceProperties` in a
`java.util.Properties` and, since 6.3.1, copies them with `putAll` rather than
`setProperty(key.toString(), value.toString())` — done so a Snowflake `PrivateKey` object could
survive the trip. pgjdbc reads its settings with `Properties.getProperty`, which returns `null` for
any value that is not a `String`. The entry is present and invisible. Measured: with the `Int` the
bound never fired and the spike abandoned the attempt; with `"30"` it fired on time. The
configuration was almost certainly correct when written against an older HikariCP, and a dependency
upgrade turned it into a no-op without touching a line of code.

**The bound that did work covers less than it appears to.** `defaultQueryTimeout` cancelled a
blocked insert at its limit — but only when the statement went through Exposed. The same insert
issued as plain JDBC on the same pool was still waiting when the spike gave up. Anything not
routed through Exposed — a health probe, a hand-written query, Flyway — is unbounded.

`playground/isolation/` supplies the reason this matters more than a slow query would suggest: a
writer conflicting with an uncommitted insert *waits*, with no error at all, holding a connection
while it waits. An unbounded wait is a connection leak with extra steps.

Three settings answer this on the server's side: `statement_timeout`, `lock_timeout`,
`idle_in_transaction_session_timeout`. Where they are set is the decision, and one option was
already eliminated: ADR-059 measured that a database-level setting does not survive
`CREATE DATABASE … TEMPLATE`, which is how every test database here is made.

## Decision

**Server bounds are carried on the connection, through pgjdbc's `options` property.**
`SessionTimeouts` renders them as `-c statement_timeout=15000ms -c lock_timeout=3000ms -c
idle_in_transaction_session_timeout=60000ms`. The pool gets all three; the numbers are layered so
each has a role that can be named: `statement_timeout` sits above Exposed's 10 s so the cheaper
client cancel usually wins and this is the backstop for statements Exposed never sees, and below
`socketTimeout` 30 s so a statement is cancelled before the socket is torn down — a cancelled
statement leaves a usable connection, a dead socket does not.

**Flyway gets `lock_timeout` and nothing else,** through `jdbcProperties`. Its connection is not
from the pool, so the pool's settings do not reach it. It needs a lock bound because
`playground/ddl-locks/` measured that PostgreSQL's lock queue is ordered: a `SELECT` arriving after
a queued `ALTER TABLE` waits behind it and dies with it. It must *not* get the other two — a
migration is supposed to hold a long transaction, and cancelling one halfway is worse than waiting.

**Every driver setting goes through `DriverProperties`, which converts to `String`,** and
`no-raw-datasource-property` forbids `addDataSourceProperty` anywhere else. The conversion happens
once instead of being remembered at each call site.

**An integration test asserts the bound is in effect,** by blocking a write behind an uncommitted
conflicting insert. With the bound present it fails `55P03` in about 3 s; with the bound removed it
fails `57014` at around 10 s, Exposed's cancel. Verified by removing it and watching the assertion
report `expected:<55P03> but was:<57014>`.

## Consequences

A statement blocked on a lock now ends in a nameable error whether or not it went through Exposed,
and the connection survives it.

The three numbers are derived from bounds already recorded rather than chosen freely, so changing
one means changing its neighbour: `statement_timeout` is meaningful only between
`defaultQueryTimeout` and `socketTimeout`.

`lock_timeout` on the pool applies to every lock wait, including a deliberate `SELECT … FOR
UPDATE`. Three seconds is short for a queue and long for a request that must answer inside §1.5's
p95 target, so a legitimate contention path that needs longer has to say so — and saying so is
better than a wait nobody bounded.

A class of bug is closed rather than avoided: a driver setting of the wrong type can no longer be
written, and if the wrapper is bypassed the arch rule fails the build.

The failure mode itself is now recorded where it will be re-encountered. A dependency upgrade
silently disabling a setting is not specific to HikariCP, and the general skill carries it as a
non-negotiable.

## Alternatives considered

**Set them on the role, in `ops/`.** One place, and it would cover psql, the migrate command and
anything else that connects. Rejected as the primary mechanism because the test container's role is
not the production role, so tests would verify a configuration nobody runs — the same reasoning
that rejected `search_path` on the database in ADR-059. Worth adding later as defence in depth,
which is a different claim from being the source of truth.

**Set them on the database with `ALTER DATABASE`.** Rejected on a measurement already recorded:
lost by `CREATE DATABASE … TEMPLATE`, so absent in every test database.

**Fix the `Int` values to strings and stop there.** The minimal correct change. Rejected because it
leaves the trap in place for the next setting added, and nothing would report the next silent
disappearance.

**`SET lock_timeout` as the first statement of every migration.** Visible in the file that needs
it. Rejected because it can be forgotten, and nothing checks — the failure this repository keeps
refusing.

**Flyway `initSql`.** Deprecated, and its documented replacement is an `afterConnect` callback:
a class to write and register in order to set one parameter that `jdbcProperties` sets directly.

**Rely on the client-side bounds alone.** Rejected on measurement: they do not cover statements
outside Exposed, and `socketTimeout` — the only one that does — destroys the connection rather than
the statement.
