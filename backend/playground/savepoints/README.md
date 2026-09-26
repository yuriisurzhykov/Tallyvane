# savepoints

## 2026-09-02 — does a savepoint really stop a failed insert from poisoning the transaction?

`UserRepository.insert`'s own KDoc calls for `InsertOutcome.EMAIL_TAKEN` decided by attempting
the write, not a preceding check — the point being to let PostgreSQL's own unique index catch a
race under `READ COMMITTED` that two sequential reads never would. PostgreSQL's error model has a
sharp edge here that is easy to discover the hard way: once any statement in a transaction raises
an error, the whole transaction is aborted — every later statement, including a plain `select`,
fails with "current transaction is aborted, commands ignored until end of transaction block"
unless the failing statement ran after a `SAVEPOINT` that gets rolled back to. Since
`GoogleSignInCompleter`/`RegisterWithPasswordUseCase` both call `insert` from inside a single
`transactions.inTransaction` that also does other reads and writes, this needed checking before
`PostgresUserRepository.insert` got written this way for real.

```bash
docker run --rm -d --name tallyvane-spike -p 5433:5432 \
  -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo -e POSTGRES_DB=demo postgres:17-alpine

./gradlew :playground:savepoints:run
```

### What the run showed

```
=== a plain insert, no conflict
  outcome: INSERTED, rows visible inside: 1
  committed rows: [first@example.com]

=== a colliding insert, caught via savepoint, then a plain read in the SAME transaction
  outcome: EMAIL_TAKEN  <- the savepoint absorbed the unique violation
  a plain read AFTER the failed insert, same transaction, did not throw: 1 rows
  committed rows: [first@example.com]

=== a colliding insert, then a SECOND, unrelated insert in the same transaction
  first insert outcome: EMAIL_TAKEN
  second insert outcome: INSERTED  <- proves the transaction was not poisoned
  committed rows: [first@example.com, second@example.com]

Pool closed. Two rows should remain: first@example.com and second@example.com.
```

Checked independently, over a plain JDBC connection rather than through Exposed:

```
$ docker exec tallyvane-spike psql -U demo -d demo -c "select address from spike_emails order by address"
      address
--------------------
 first@example.com
 second@example.com
(2 rows)
```

**What this settles:** `TransactionManager.current().connection.setSavepoint(name)` before the
`insert`, `releaseSavepoint` on success and `rollback(savepoint)` on `ExposedSQLException`, is
enough to keep a unique-violation local to that one statement. The second case's "a plain read
after the failed insert did not throw" is the load-bearing line — without the savepoint that read
would itself fail with "transaction is aborted", not merely report the wrong count. The third case
is the one that matches production shape: two more statements ran in the same transaction after
the failed insert, one of which wrote, and both succeeded.

This is the mechanism `PostgresUserRepository.insert` uses for real.
