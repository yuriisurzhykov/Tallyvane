# health

## 2026-08-25 — how do the pieces of a health probe actually fit together?

Slices 4, 5 and 10 built the parts — `HealthCheck`, the `Bounded` and `Contained` decorators,
`HealthReporter.OverChecks`, `Ailment`, and the first two real checks — but nothing has ever
assembled them, because `app` does not exist. So the arrangement existed only as prose in three
ADRs, and two claims in that prose had never been observed: that the checks run at once rather
than one after another, and that a failing dependency cannot take the probe down with it.

This spike is the composition root that slice 13 will contain, written by hand. (The commands below
have moved to the 2026-08-26 entry, which describes what the spike does now.)

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

```

The run above also printed the two endpoint bodies, written by hand — with a note admitting the
rendering belonged to a later slice and this was not it. That note is now obsolete; see the next
entry.

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

## 2026-08-26 — the endpoints exist, so the hand-written JSON had to go

Slice 12 built `platform:health`, and with it the rendering this spike had been approximating. Two
copies of a wire format is one too many: the approximation could go on looking right while the real
`Presented` drifted, and nothing would have noticed. So the spike now keeps the same five checks and
the same console table, then hands the reporter to the real `HealthRoutes` behind the real `Api` and
serves on a port.

```bash
docker run --rm -d --name tallyvane-health -p 5441:5432 \
  -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo -e POSTGRES_DB=demo \
  postgres:17-alpine postgres -c max_connections=20

./gradlew :playground:health:run
```

The console phase runs first and prints the same three reports as before; then the menu lists the
requests worth making. Verbatim from the run, headers trimmed to what carries information:

```
$ curl.exe -i localhost:9031/api/v1/health
HTTP/1.1 200 OK
traceparent: 00-01a03d90c835710a9bb6468a95fd5de4-a08e098cb0f24b1d-01
Cache-Control: no-store
Content-Type: application/json

{"status":"down"}

$ curl.exe localhost:9031/api/v1/health -H "x-service-token: spike-service-token"
{"status":"down","ready":false,"checks":[
  {"name":"database","status":"up","took_ms":5},
  {"name":"schema","status":"up","took_ms":100},
  {"name":"llm","status":"down","took_ms":2016,"cause":{"kind":"overran","bound_ms":2000}},
  {"name":"storage","status":"down","took_ms":0,"cause":{"kind":"threw","type":"IllegalStateException"}},
  {"name":"outbox","status":"degraded","took_ms":0,"cause":{"kind":"refused","says":"queue depth 1420, oldest 9m"}}]}
   >>> request took 2060 ms

$ curl.exe localhost:9031/api/v1/health -H "x-service-token: wrong"
{"status":"down"}

$ curl.exe -i localhost:9031/api/v1/health/ready
HTTP/1.1 503 Service Unavailable
Cache-Control: no-store

{"status":"down"}
```

(The `checks` array arrives on one line; it is broken up here to be readable.)

### What the real endpoints showed that the hand-written pair could not

**The summary is 17 bytes, and `storage` threw a message.** The stub's exception says
`bucket unreachable`; the unauthenticated body is `{"status":"down"}` and contains no check name, no
message, and no count. The hand-written version asserted this by construction — it only ever printed
one field — whereas this went through `Presented` and the serializer, which is where a leak would
actually come from.

**Parallelism is visible through the endpoint.** The `took_ms` sum to 2121 ms while the request took
2060 ms. Same conclusion as the console phase, now measured on the path a monitor uses.

**Liveness is free and readiness is not, by a factor of a hundred.** Three calls to `/health/live`
took 31, 16 and 34 ms — that is `curl` starting up, not the server working. Two calls to
`/health/ready` took 2049 and 2027 ms. This is the strongest form of the argument in `HealthRoutes`'
KDoc for a liveness probe that consults nothing: the frequent probe costs nothing measurable, and
the expensive one is the one asked rarely.

**A wrong token is a 200, not a 401.** Third command above. ADR-063 decided this; the point of
seeing it is that a monitor holding a stale token degrades to the public answer instead of reporting
an outage that is not happening.

**`Cache-Control: no-store` is on all three**, including the 503.

### What the migration exposed on the way

`PostgresPersistence` fails fast in its constructor, so the first run — against a database that was
not up — did not serve a health endpoint reporting a down database. It threw during startup and the
process exited. That is Hikari's `initializationFailTimeout` doing what it is set to do, and it is
correct for a pool, but it means `/health` can never carry "the database is down" at boot: an
application that cannot reach Postgres does not reach the point of listening. Whether `app` should
start anyway and let readiness answer 503 is a slice 13 decision, recorded in the plan.
