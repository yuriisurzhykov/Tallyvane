# Concurrency: what the default isolation level does not protect

Everything here was measured on PostgreSQL 17 with two JDBC connections, not read. The
outputs are quoted because the SQL states are what code has to branch on.

## Checking before inserting is a race, always

`READ COMMITTED` is the default, and under it a transaction sees rows committed before each
*statement* — so "is this address free?" answers about the past, not about the moment the
insert lands.

```
=== READ COMMITTED, no unique constraint: check then insert
  A sees rows for ivan@x: 0
  B sees rows for ivan@x: 0
  rows afterwards: 2  <- both got in
```

Neither session did anything wrong. There is no isolation level short of `SERIALIZABLE` that
makes this pattern safe, and raising the level to fix it is the expensive answer to a problem
a constraint solves for free.

**Uniqueness is enforced by a unique index, never by a preceding `select`.** Where a value has
to be unique, declare it unique and treat the violation as the answer: `insert … on conflict do
nothing` when a duplicate is acceptable, or catch SQL state `23505` when the caller needs to
know. The same holds for any invariant the database can express — a check constraint, a foreign
key, an exclusion constraint. A guard in Kotlin protects against nothing that arrives
concurrently.

## A conflicting insert waits; it does not fail fast

This is the part that surprises, and it changes how a write path must be bounded.

```
=== READ COMMITTED, unique index: the same race
  B while A is still open: PSQLException sqlState=57014 ERROR: canceling statement due to statement timeout
  B after A committed: PSQLException sqlState=23505 ERROR: duplicate key value violates unique constraint "people_email"
  rows afterwards: 1  <- only one got in
```

While A holds an uncommitted insert of the same key, B **blocks** — uniqueness cannot be
decided until A's fate is known. B received no error at all until a `statement_timeout` cut it
off with `57014`; the honest `23505` only arrived once A had committed.

Two consequences.

A slow transaction does not merely occupy a connection: it stalls every other writer competing
for the same key, and those writers occupy connections too. That is how one slow path becomes a
pool exhaustion.

Therefore every write path needs a **server-side** bound. A coroutine timeout cannot interrupt
a blocked JDBC call, so `statement_timeout` is what turns an unbounded wait into an error the
code can handle. Set it on the role or the database rather than hoping every call site sets it.

## Serialization failures are a protocol, not a bug

Under `SERIALIZABLE`, two transactions that read overlapping data and write based on it cannot
both be allowed to commit:

```
=== SERIALIZABLE: both read the sum, both write from it
  A read: 0
  B read: 0
  B commit: PSQLException sqlState=40001 ERROR: could not serialize access due to read/write dependencies among
  retry of the failed one: succeeded on the second attempt
```

PostgreSQL's contract is that the loser retries. So a blanket "never retry a transaction" is
wrong, and so is a library retrying blocks on any `SQLException`.

**The rule with its exception.** A transaction block that decides and then writes must not be
re-executed automatically by a library — a retry repeats every non-database thing the block did
as well. Turn that off (`defaultMaxAttempts = 1` in Exposed). Then, *where a block is written to
be safe to re-run*, retry deliberately and only on:

- `40001` — serialization failure;
- `40P01` — deadlock detected.

Never on `23505`, `23503` or anything else: those are answers, and retrying them repeats a
question that has already been decided.

Retry the **whole transaction**, from the read, since the point is to re-derive the decision
from a new snapshot. Bound the attempts and log the count — a serialization failure rate that
grows is a design signal, not noise.

`SERIALIZABLE` is worth reaching for when an invariant spans rows a constraint cannot express —
"no more than N active per user", "these balances must sum to zero". For a single-row
uniqueness, a unique index is cheaper and needs no retry at all.

## Nothing that talks to the network inside a transaction

*Reasoned, not measured.* A transaction holds a connection and a snapshot for its whole
lifetime. An HTTP call, a model call or a file upload inside one makes that lifetime depend on
something outside the database's control: the pool drains, `idle in transaction` accumulates,
and vacuum cannot clean rows those snapshots still see.

Decide inside the transaction, and record the intent to act; perform the effect afterwards, from
a queue that can retry it. That is what a transactional outbox is for.

## Reviewing a write path

1. Which invariant does this rely on, and is it declared in the schema or merely checked in
   Kotlin?
2. If two callers ran this at the same moment, what does the database do — accept both, block
   one, or refuse one?
3. If it blocks, what bounds the wait, and is that bound server-side?
4. On `40001` or `40P01`, is the whole transaction re-derived, or is a stale decision
   re-applied?
5. Does anything inside the transaction cross the network?
