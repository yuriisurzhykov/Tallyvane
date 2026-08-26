# ddl-locks

## 2026-08-25 — what does changing a schema do to a running application?

§8.22 promises schema changes without downtime, and two claims stood between that promise and
anything checkable: that a queued `ALTER TABLE` blocks readers arriving behind it, and that
`CREATE INDEX CONCURRENTLY` cannot be run from a Flyway migration. Both were beliefs.

```bash
docker run --rm -d --name tallyvane-ddl -p 5437:5432 \
  -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo -e POSTGRES_DB=demo postgres:17-alpine

./gradlew :playground:ddl-locks:run                          # the lock experiment
./gradlew :playground:ddl-locks:run -Pspike.flyway=true      # also the Flyway half, which hangs on purpose
```

The Flyway half is off by default because its answer is "it hangs", and a spike that hangs by
default is a spike nobody runs twice.

## What the run showed: locks

```
=== ALTER TABLE behind an open reader, without lock_timeout
  the ALTER: sqlState=57014 ERROR: canceling statement due to statement timeout
  a plain SELECT arriving after it: sqlState=57014 ERROR: canceling statement due to statement timeout

=== ALTER TABLE behind an open reader, with lock_timeout=300ms
  the ALTER: sqlState=55P03 ERROR: canceling statement due to lock timeout
  a plain SELECT arriving after it: succeeded
```

The second line of the first block is the finding. That `SELECT` is compatible with the
transaction actually holding the table — it would have run instantly on its own. It failed
because PostgreSQL's lock queue is ordered and the `ALTER` was ahead of it. One long query plus
one `ALTER TABLE` is an outage, and it lasts as long as the DDL is prepared to wait.

With `lock_timeout` the DDL gives up in 300 ms and reads never notice. A migration that fails is
rerunnable; an application stalled behind a lock queue is an incident.

## What the run showed: Flyway and CONCURRENTLY

Inside a transaction, Flyway refuses before executing anything:

```
migrate: FlywayException Detected both transactional and non-transactional statements
         within the same migration
```

With `executeInTransaction=false` it accepts the migration and never returns. What it is waiting
for is visible from another session:

```
pid 65 | idle in transaction | ClientRead      | SELECT COUNT(*) FROM pg_namespace WHERE nspname=$1
pid 66 | active              | Lock/virtualxid | create index concurrently ... on widgets
```

`CREATE INDEX CONCURRENTLY` waits for every transaction that could see the table to end. pid 65
is **Flyway's own connection**, idle inside a transaction — that `pg_namespace` query is its
schema-existence check. The migration waits on the tool running it, indefinitely.

So concurrent index creation is an operational step outside the migration tool. Worth knowing as
well: a failed `CREATE INDEX CONCURRENTLY` leaves an invalid index that must be dropped before
retrying, which is a second reason it does not belong in something automated.

## A wrong turn worth keeping

This spike hung too, the same way the isolation one did, and the fix generalises: the bound now
lives in the JDBC URL — `?options=-c statement_timeout=5000` — rather than in a statement my own
blocked code was supposed to reach. Nothing this spike does can wait forever, whatever order its
own logic runs in.
