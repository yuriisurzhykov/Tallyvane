# pool-occupancy

## 2026-08-25 — what does one `PostgresPersistence` cost the server, at rest?

`max_connections=20` from §16.2's memory budget was about to be applied to the test container,
and four integration cases failed with `FATAL: sorry, too many clients already`. The explanation
offered at the time — seven pools of eight — was arithmetic, not a measurement, and it left the
question that actually matters unanswered: how many connections does one pool hold when nothing
is using it, and what does that mean for a server allowed twenty?

```bash
docker run --rm -d --name tallyvane-pool -p 5440:5432 \
  -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo -e POSTGRES_DB=demo \
  postgres:17-alpine postgres -c max_connections=20

./gradlew :playground:pool-occupancy:run
```

The count comes from `pg_stat_activity` over a connection of its own, so the pool being measured
cannot be the thing reporting on it.

## What the run showed

```
max_connections on this server: 20
PostgresPersistence pool size:  8

=== one PostgresPersistence, the way the application will own it
  before anything is built:                            0
  straight after the constructor returns:              2
  after 2000ms of housekeeping, still no query:        8
  after one trivial transaction:                       8
  after close():                                       0

=== two of them at once, which is what a rolling deploy looks like
  two instances, idle:                                 16

=== adding them one at a time, which is what one conformance spec does today
  instance 1 of 7 built, all idle:                     8
  instance 2 of 7 built, all idle:                     16
  with 3 instances built, the server had no slot left:
    FATAL: sorry, too many clients already
  3 x 8 exceeds the limit (unreadable now: PSQLException), and the refusal fell on the counter's own
  connection - a spec holding this many pools cannot even be observed

=== the same pool size, but minimumIdle=1
  idle, never asked for a connection:                  1
  after borrowing one connection:                      1

=== a pool of one, which is all a sequential test needs
  idle:                                                1
  everything closed:                                   0
```

## What to read in it

**A pool at rest costs its full size.** Not one connection, not "as many as are in use" — eight,
within two seconds of construction, with no query ever issued. HikariCP documents `minimumIdle` as
defaulting to `maximumPoolSize`, and recommends leaving it that way so the pool behaves as a fixed
size. The constructor itself opens one or two; the housekeeper fills the rest.

**Two instances hold sixteen of twenty slots.** That is not a test-only number: a rolling deploy
has the old container alive while the new one starts. Four slots remain for the migration command,
a `psql` session, and anything watching. This is worth knowing before it happens at 2 a.m. rather
than during.

**Three instances cannot even be observed.** The refusal did not fall on the third pool — it fell
on the counter's own connection. A test suite in that state produces a failure whose message is
about clients rather than about whatever the test was checking.

**`minimumIdle=1` costs exactly one, and borrowing one does not change that.** So the eight are a
deliberate choice about warmth, not a requirement of the pool. A sequential test needs one
connection and currently reserves eight.

## What this does not settle

Whether pool size should stay a constant inside `PostgresPersistence` or become configuration is a
decision, not a measurement, and this spike only supplies the numbers for it. Same for
`minimumIdle`: the trade is warm-start latency against slots held while idle, and §1.5's p95
targets are the thing to weigh it against — measured under load, which this spike does not do.
