# transactions

## 2026-08-25 — does `TransactionRunner` really commit and roll back?

The question was not whether the tests pass. It was whether a rollback reaches the
database at all, or whether the write simply never happened and the row count agreed with
us by accident. A passing test that counts rows cannot tell those two apart on its own.

So each step here prints three numbers: the committed rows before the block, the rows the
transaction itself can see from inside it, and the committed rows after. The middle one is
the load-bearing one — it is what proves the write occurred before being undone.

Run it against a Postgres you started yourself:

```bash
docker run --rm -d --name tallyvane-spike -p 5433:5432 \
  -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo -e POSTGRES_DB=demo postgres:17-alpine

./gradlew :playground:transactions:run
```

Any other database: `-Pspike.url=jdbc:postgresql://host:port/db -Pspike.user=… -Pspike.password=…`.

## What the run showed

```
=== commit: one write, verdict Commit
  committed rows before: 0
  inside the transaction, rows visible to it: 1
  committed rows after:  1  <- the write survived

=== rollback: one write, verdict Rollback
  committed rows before: 1
  inside the transaction, rows visible to it: 2
  committed rows after:  1  <- the write was undone

=== failure: one write, then an exception
  committed rows before: 1
  inside the transaction, rows visible to it: 2
  the exception reached the caller: deliberate failure
  committed rows after:  1  <- the write was undone

=== nesting: a write, then inTransaction inside inTransaction
  committed rows before: 1
  inside the transaction, rows visible to it: 2
  refused: A transaction is already open. …
  committed rows after:  1  <- the refusal took the outer write down with it
```

Afterwards the table holds exactly one row, `1`, the one the commit wrote. Checked
independently:

```bash
docker exec tallyvane-spike psql -U demo -d demo -c "select n from spike_rows"
```

The nesting case is worth reading twice. The outer transaction had already written when
the nested call was refused, and that write is gone too — correctly, because the block
never reached its verdict. An implementation that let nesting join the outer transaction,
which is Exposed's default, would have committed it.

A first version of this output printed only a running total and no inside-the-transaction
count, which made "undone" and "never written" indistinguishable — the exact confusion the
spike existed to remove.

## 2026-09-02 — can a "dumb" repository (no `transaction { }` of its own) actually work?

The identity persistence slice needs every repository method to be a plain Exposed DSL
call with no transaction management of its own — the use case that calls it opens the one
transaction covering its whole flow, reads included, per `GoogleSignInCompleter`'s real
shape (a read, then a conditional write). Asked and answered in chat before writing this,
rather than trusted from the architecture alone: does an unwrapped Exposed call actually
throw with nothing open, and does it actually join rather than open a second one when
something is?

```bash
docker run --rm -d --name tallyvane-spike -p 5433:5432 \
  -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo -e POSTGRES_DB=demo postgres:17-alpine

./gradlew :playground:transactions:run
```

### What the run showed

```
=== a 'dumb' method (no transaction { } of its own) called with none open
  threw IllegalStateException: No transaction in context.

=== the same 'dumb' method called from inside transactions.inTransaction
  did NOT throw: read 1 rows, using the ambient transaction TransactionRunner opened

=== one use case, one inTransaction, covering a dumb read AND a dumb write

=== read-then-write: mirrors GoogleSignInCompleter.findOrCreateUser's real shape
  committed rows before: 1
  dumb read, from inside the same block that will also write: 1 rows so far
  dumb write, same block, same ambient transaction: 2 rows now visible to it
  committed rows after:  2  <- read and write shared one transaction, no repository opened its own
```

Checked independently, over `psql` rather than through Exposed:

```
$ docker exec tallyvane-spike psql -U demo -d demo -c "select n from spike_rows order by n"
 n
---
 1
 5
(2 rows)
```

`1` is the first step's commit; `5` is the read-then-write step's write. Both real, both
survived, nothing double-counted.

**What this settles:** a repository method with no `transaction { }` of its own genuinely
requires an ambient one — Exposed throws immediately otherwise, it does not silently do
nothing. And when a use case opens exactly one `transactions.inTransaction` around its
whole flow, every plain Exposed call inside it — read or write, however many — shares that
one transaction without any of them needing to know the others exist. This is what makes
`SessionIssuer`, `GoogleSignInCompleter`, and every future repository safe to write with no
transaction-management code of their own: the use case is the only place that ever calls
`transactions.inTransaction`, and everything it calls just runs.
