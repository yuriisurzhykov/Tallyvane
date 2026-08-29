# server

The composition root: the one place that knows which implementation satisfies which port, reads the
settings, opens the socket and gives everything back on the way out.

Renamed from `app` on 2026-08-28 — see the dated entry below. Every mention of the rule
`app-has-no-logic` elsewhere in this repository, including in ADR-010, keeps that name: it is a
stable rule identifier, not a claim about which directory it currently checks.

## What was needed, and why nothing existing would do

Twelve slices produced a platform that could not be run. Persistence, observability, the HTTP layer,
the error contract and the health endpoints all existed and were tested, and nothing assembled them,
because assembling them is a job with an owner — and the owner did not exist. Every debt those slices
left behind was the same debt in different words: *there is nobody to tell this number to*, *there is
nobody to close this*, *there is nobody to decide this*.

The two spikes under `playground/` had each written this root by hand to answer one question. Both fit
on a screen, which is the measurement ADR-010 rests on.

## What is here

Five classes, and the names are not a choice: `app-has-no-logic` admits only names ending in `Wiring`,
`Configuration` or `Application` under `server/src/`, and `one-top-level-class` admits one type per file.

```
config/EnvironmentConfiguration.kt   reads the environment, refuses all of it at once
config/Configuration.kt              values already known to be good
PlatformWiring.kt                    the platform's objects: built once, closed once
Wiring.kt                            the root, one line per capability
Application.kt                       the process: level, socket, shutdown
resources/logback.xml                includes the fragment observability owns
```

The split between the two `config` classes was forced by a gate rather than chosen: reading needs
branching — "if this variable is absent, remember its name and keep going" — and `no-companion-logic`
refuses a companion factory that branches. So one type reads and validates and the other only holds.
The rule found a conflated responsibility before the author did.

## Running it locally

Four steps, and **all of them in one shell session** — the two Gradle invocations read the same
environment, and setting the variables in a different window is the mistake that looks like a bug.

```powershell
docker run --rm -d --name tallyvane-db -p 5432:5432 `
  -e POSTGRES_USER=tallyvane -e POSTGRES_PASSWORD=local-dev-password `
  -e POSTGRES_DB=tallyvane postgres:17-alpine

$env:TALLYVANE_DB_URL       = "jdbc:postgresql://localhost:5432/tallyvane"
$env:TALLYVANE_DB_USER      = "tallyvane"
$env:TALLYVANE_DB_PASSWORD  = "local-dev-password"
$env:TALLYVANE_HEALTH_TOKEN = "t" * 40

cd backend
./gradlew :migrate:run     # applies migrations; the application never does
./gradlew :server:run      # listens on 8080 unless TALLYVANE_HTTP_PORT says otherwise
```

Then, in a browser:

```
http://localhost:8080/api/v1/health         {"status":"up"} — one field, no token presented
http://localhost:8080/api/v1/health/live    {"status":"up"} — touches nothing
http://localhost:8080/api/v1/health/ready   {"status":"up"} with 200, or 503 before migrations
```

The detailed report needs a request header, which a browser address bar cannot send:

```powershell
curl.exe http://localhost:8080/api/v1/health -H "x-service-token: $env:TALLYVANE_HEALTH_TOKEN"
```

Skip `:migrate:run` to watch readiness answer 503 and the breakdown say `schema: down`; run it
afterwards and readiness turns 200 **without restarting the process**.

`Ctrl+C` stops the application; `docker rm -f tallyvane-db` removes the database.

## 2026-08-26 — the decisions this slice settled

**Wiring is written by hand and checked by the compiler** ([ADR-010](../../docs/adr/ADR-010-manual-wiring.md)).
Containers, providers and modules as structure; nothing resolving at runtime. Koin and a hand-rolled
service locator are refused because both turn "forgot a dependency, so it did not compile" into a
runtime failure. Metro and kotlin-inject are **deferred, not rejected**, with the condition named.

**The process comes up even when its database does not answer.** Before this slice the pool acquired a
connection in its constructor, so a process pointed at a stopped database died before it could listen —
measured in `playground/health`. The consequence was that `/health/ready` could never report the one
state it exists for: running, and unable to serve. `initializationFailTimeout` is negative now, and
`PlatformWiring` builds the pool lazily, so nothing at startup touches the database.

A second effect came for free and is now pinned by a test: the pool recovers by itself, so a database
that comes back turns readiness green again without a restart.

**The pool size is told to the class, not known by it.** Eight is a property of the server §16.2 budgets
for, and the class owns only the invariant that the dispatcher's parallelism follows it.

**A refusal names every missing variable, not the first.** Verified live against an empty environment:

```
java.lang.IllegalStateException: Refusing to start.
  - TALLYVANE_DB_URL is not set
  - TALLYVANE_DB_USER is not set
  - TALLYVANE_DB_PASSWORD is not set
  - TALLYVANE_HEALTH_TOKEN is not set
```

`:migrate` still fails on the first one it meets; unifying that belongs with the deploy order.

**A refusal never quotes a value.** The message names the variable and what was expected of it. The
password and the health token are `Secret` (in `platform:kernel`), whose `toString` is `***` — added
because `DatabaseAccess` was a `data class` holding a plain `String` password, so any log line carrying
the whole object printed it.

**The health token is mandatory and at least forty characters.** An absent token closes the detailed
report, which `ServiceToken` does deliberately for a misconfigured deploy; a short one opens it to
anybody willing to guess. Both are now refused at startup instead.

## What the live run showed

Against a real database, before migrations were applied:

```
GET /api/v1/health/live   200
GET /api/v1/health/ready  503
GET /api/v1/health        {"status":"down","ready":false,"checks":[
                            {"name":"database","status":"up","took_ms":5},
                            {"name":"schema","status":"down","took_ms":102}]}
```

Then `:migrate` ran once, and without restarting the process:

```
GET /api/v1/health/ready  200
GET /api/v1/health        {"status":"up","ready":true, …both checks up… }
```

That is ADR-051's ordering demonstrated rather than asserted: the deploy applies migrations, the
application verifies them, and readiness is the difference.

## Tests, and one that was wrong

Fifteen cases need no Docker — configuration, and the whole of "comes up with no database", because
there is nothing to start. Eight run against a container.

Two are worth naming. `PlatformWiringSpec` counts connections **on the server**, from a connection of
its own, so the pool cannot satisfy the case by agreeing with itself. And the closing case was wrong at
first: it ran `inTransaction { Verdict.Commit(Unit) }`, which passed against a *closed* pool. Exposed
opens a connection when a statement needs one, so an empty block never asks the pool for anything. The
case issues a real `select 1` now, and was then checked by removing the `close()` and watching it fail.

## 2026-08-28 — renamed from `app`

The module was called `app` from slice 13 until this date. It confused a reading of "which of these
four running services is `app`" against `frontend-app` (the console, `app.<domain>`) — two unrelated
things sharing one word in conversation and in `ops/`. Renamed the Gradle module, the package
(`tallyvane.app` → `tallyvane.server`) and every ops/CI reference alongside it
(`docker-compose.yml`'s `app`/`app-blue`/`app-green`, `apply.sh`, `deploy-wrapper.sh`, the nginx
upstream, `publish-backend.yml`).

Left alone, deliberately: the architecture rule's own id, `app-has-no-logic` — renaming it would have
meant rewriting three ADRs (ADR-010, ADR-047, ADR-063) that cite it by name, and an ADR is a record of
a decision at the time it was made, not a document that tracks a later rename. Its *logic* (which
path and package it actually checks) was updated to `server/src/` and `tallyvane.server..`; only the
string naming the rule stayed put. The public hostname `app.<domain>` also stayed put — it names
`frontend-app`'s URL, which this rename has nothing to do with.

## Not here

**The full schema-drift run.** It needs the process to find every `Table` object on the classpath, and
how to scan is an unmade decision — see `backend/.plans/`. With no capability module in existence the
test could only assert that a scan found nothing, which is a canary, not coverage.

**`minimumIdle`.** Whether to hold the whole pool warm or one connection is a trade against §1.5's p95
and has to be measured under load, not chosen.

**Per-check bounds for health.** Uniform two seconds today, which is a compromise rather than a
decision: a `select 1` and a model call do not deserve the same patience.

**The access log.** Now nginx's job — see `backend/.plans/backend-infra-cache-wiring.md`.
