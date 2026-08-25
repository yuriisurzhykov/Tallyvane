# isolation

## 2026-08-25 — what does READ COMMITTED actually allow, and when is retrying correct?

Three claims were about to be written into a skill on the strength of remembering them: that
checking before inserting is a race, that a unique constraint is the real guard, and that a
serialization failure is the one legitimate reason to retry a transaction. None of them had been
observed in this project, so none of them counted.

```bash
docker run --rm -d --name tallyvane-iso -p 5436:5432 \
  -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo -e POSTGRES_DB=demo postgres:17-alpine

./gradlew :playground:isolation:run
```

Any other database: `-Pspike.url=jdbc:postgresql://host:port/db -Pspike.user=… -Pspike.password=…`.

## What the run showed

```
=== READ COMMITTED, no unique constraint: check then insert
  A sees rows for ivan@x: 0
  B sees rows for ivan@x: 0
  rows afterwards: 2  <- both got in

=== READ COMMITTED, unique index: the same race
  B while A is still open: PSQLException sqlState=57014 ERROR: canceling statement due to statement timeout
  B after A committed: PSQLException sqlState=23505 ERROR: duplicate key value violates unique constraint "people_email"
  rows afterwards: 1  <- only one got in

=== SERIALIZABLE: both read the sum, both write from it
  A read: 0
  B read: 0
  B commit: PSQLException sqlState=40001 ERROR: could not serialize access due to read/write dependencies among
  retry of the failed one: succeeded on the second attempt
```

The middle block is the one worth reading twice, and it is the part nobody writes down. While A
holds an uncommitted insert of the same key, B does **not** get a duplicate-key error — it
waits, because uniqueness cannot be decided until A's fate is known. No error arrived at all
until `statement_timeout` cancelled it with `57014`; the honest `23505` came only after A
committed. So a constraint is the real guard, and the price of relying on it is a wait that only
a server-side timeout bounds.

The third block settles the retry question: `40001` is PostgreSQL asking the loser to run again,
and running again worked. A blanket "never retry a transaction" would be wrong, and a library
retrying on any `SQLException` would be worse.

## A wrong turn worth keeping

The first version of the second experiment hung. A inserted without committing, B inserted the
same value and blocked — and the line committing A came *after* B's blocked call, so the two
waited on each other until the container was killed. The lesson is not about Postgres: a
measurement of waiting must not be able to wait indefinitely. The bound now lives in a
`statement_timeout` set before the blocking statement, so every path terminates with an answer.
