# health

## 2026-08-25 — how do the pieces of a health probe actually fit together?

Slices 4, 5 and 10 built the parts — `HealthCheck`, the `Bounded` and `Contained` decorators,
`HealthReporter.OverChecks`, `Ailment`, and the first two real checks — but nothing has ever
assembled them, because `app` does not exist. So the arrangement existed only as prose in three
ADRs, and two claims in that prose had never been observed: that the checks run at once rather
than one after another, and that a failing dependency cannot take the probe down with it.

This spike is the composition root that slice 13 will contain, written by hand.

```bash
docker run --rm -d --name tallyvane-health -p 5441:5432 \
  -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo -e POSTGRES_DB=demo \
  postgres:17-alpine postgres -c max_connections=20

./gradlew :playground:health:run
```

Two of the five checks are real, against that container. Three are stubs, each failing in a
different way — a way that is awkward to arrange with a real dependency and trivial to arrange
with a stub: one takes 10 s, one throws, one reports `degraded`.

## What the run showed

Flyway's own log lines are omitted here; everything else is verbatim.

```
=== only the two real checks, on a database nobody migrated
  aggregate: down  { kind: dependencies, names: [schema] }
  ready:     false
  report took 980 ms, checks summed to 1067 ms
  checks:
    database  up          129 ms
    schema    down        938 ms   { kind: behind, versions: [20260825020000] }

--- applying migrations, the way the deploy would
    applied 1 migration(s)

=== the same two checks, after the deploy applied them
  aggregate: up
  ready:     true
  report took 153 ms, checks summed to 160 ms
  checks:
    database  up            7 ms
    schema    up          153 ms

=== five checks: two real, three stubbed to fail in different ways
  aggregate: down  { kind: dependencies, names: [llm, storage, outbox] }
  ready:     false
  report took 2004 ms, checks summed to 2319 ms
  checks:
    database  up           85 ms
    schema    up          147 ms
    llm       down       2003 ms   { kind: overran, bound_ms: 2000 }
    storage   down         84 ms   { kind: threw, type: IllegalStateException }
    outbox    degraded      0 ms   { kind: refused, says: "queue depth 1420, oldest 9m" }

=== the same report, as the two endpoints would render it
  what an unauthenticated caller sees:
    { "status": "down" }
  what an authorised reader sees:
    { "status": "down", "ready": false, "checks": [
        { "name": "database", "status": "up", "took_ms": 85 },
        { "name": "schema", "status": "up", "took_ms": 147 },
        { "name": "llm", "status": "down", "took_ms": 2003, "cause": { kind: overran, bound_ms: 2000 } },
        { "name": "storage", "status": "down", "took_ms": 84, "cause": { kind: threw, type: IllegalStateException } },
        { "name": "outbox", "status": "degraded", "took_ms": 0, "cause": { kind: refused, says: "queue depth 1420, oldest 9m" } },
    ] }
```

The JSON is written by hand in the spike. Rendering belongs to slice 12 and this is not it — it
is here to show which fields cross which boundary (ADR-055), not to fix a format.

## What to read in it

**The checks run at once.** The report took 2004 ms while the individual checks summed to
2319 ms. A sequential aggregator could not produce a total below the sum, so this is the
evidence for a claim that was previously only asserted. The 2004 ms is the slowest check, not
the total work.

**A dependency that throws does not reach the caller.** `storage` threw
`IllegalStateException`, and the report carries `{ kind: threw, type: IllegalStateException }` —
the type only, no message. That is `Contained`, and it is why the other four checks still have
answers.

**A dependency that hangs is answered without waiting for it.** `llm` sleeps 10 s; the report
came back at 2 s with `overran`. That is `Bounded`, and the last lines of the run state the
limitation honestly: the abandoned work is still occupying a thread afterwards. Cancelling the
scope stops anyone *waiting* for it, not the work itself.

**`requiredForReadiness` is what separates "degraded" from "not ready".** `llm` and `outbox`
are ailing and not required, `storage` is required and down — so `ready` is false because of
`storage` alone. Had `storage` been up, the aggregate would have been `degraded` with
`ready: true`, which is the whole reason for three states rather than two.

**The schema check is the slow one, and now there is a number for it.** 938 ms on first call,
147 ms warm, because Flyway opens its own connection every time rather than borrowing from the
pool. At probe frequency that is affordable; it is also why the 2 s bound is not generous.

## What this does not settle

The bound of 2 s, and whether every check should share one. Here it is uniform because it was
easier to write, not because it was decided; a model call and a `select 1` do not deserve the
same patience. Slice 13 owns that, with `app`.
