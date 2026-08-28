# observability-otel

No `build.gradle.kts` here, unlike every other entry under `playground/` — deliberately. The
question below is about the OpenTelemetry Java agent's own behaviour against code that already
exists (`:app`), not about new Kotlin logic to write and compile, so there is nothing for a spike
module to hold. The dated entry below is this investigation's record instead, per
`.cursor/rules/development-methodology.mdc`.

## 2026-08-28 — does the OTel Java agent's own MDC injection collide with `TraceContext`?

Section 6 of the CD/blue-green/security/observability plan chose the full OpenTelemetry SDK —
real spans and auto-instrumentation of HTTP and JDBC — over log-shipping alone. `TraceContext`
([ADR-056](../../../docs/adr/ADR-056-request-identity.md)) already writes `trace_id`/`span_id`
into slf4j's MDC, in the same two key names the OpenTelemetry Java agent's own Logback
instrumentation injects by default (confirmed from the project's own docs before running
anything: `docs/logger-mdc-instrumentation.md` in `open-telemetry/opentelemetry-java-instrumentation`
lists `trace_id`/`span_id`/`trace_flags` as the exact, unconfigurable-by-default MDC keys the
agent's `logback-mdc` instrumentation writes). Two independent mechanisms writing the same two
keys is a real collision to check, not a hypothetical one — and documentation could not settle
*which one wins*, or whether Ktor's CIO engine (this backend's own choice, ADR-050) is even
instrumented at all versus only the more commonly-tested Netty engine.

**Reproduced against the real `:app`**, not a toy program, so the answer is about this codebase
and not about OpenTelemetry in the abstract:

```powershell
# A throwaway Postgres, port 5433 so it cannot collide with a real deployment or the docker
# compose stack already running locally.
docker run --rm -d --name tallyvane-db-spike -p 5433:5432 `
  -e POSTGRES_USER=tallyvane -e POSTGRES_PASSWORD=local-dev-password `
  -e POSTGRES_DB=tallyvane postgres:17-alpine

curl.exe -L -o opentelemetry-javaagent.jar `
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.31.1/opentelemetry-javaagent.jar

$env:TALLYVANE_DB_URL = "jdbc:postgresql://localhost:5433/tallyvane"
$env:TALLYVANE_DB_USER = "tallyvane"
$env:TALLYVANE_DB_PASSWORD = "local-dev-password"
$env:TALLYVANE_HEALTH_TOKEN = "t" * 40
cd backend
./gradlew.bat :migrate:run

$env:JAVA_TOOL_OPTIONS = "-javaagent:$PWD/opentelemetry-javaagent.jar"
$env:OTEL_SERVICE_NAME = "tallyvane-app-spike"
$env:OTEL_TRACES_EXPORTER = "logging"     # prints every span to stdout; no collector needed to answer this question
$env:OTEL_METRICS_EXPORTER = "none"
$env:OTEL_LOGS_EXPORTER = "none"
./gradlew.bat :app:run

# in a second shell:
curl.exe -i http://localhost:8080/api/v1/health/ready
curl.exe http://localhost:8080/api/v1/health -H "x-service-token: $('t' * 40)"
```

## What the run showed

The agent instruments both things it was being asked to answer for, unprompted by any
configuration beyond attaching the jar — confirmed by the tracer names in its own span output,
not assumed from the library name:

```
[otel.javaagent] ... 'GET /api/v1/health/ready' : 7411a6a7b2d8326b119c018a8da88ee9 7e76b27f8f5be582 SERVER
  [tracer: io.opentelemetry.ktor-3.0:2.31.1-alpha] AttributesMap{... http.route=/api/v1/health/ready ...}
[otel.javaagent] ... 'SELECT tallyvane' : 7411a6a7b2d8326b119c018a8da88ee9 0ce515dcacaec124 CLIENT
  [tracer: io.opentelemetry.jdbc:2.31.1-alpha] AttributesMap{... db.statement=select current_catalog ...}
```

Ktor's **CIO** engine (this backend's own choice) is instrumented — the tracer name is
`io.opentelemetry.ktor-3.0`, generic to Ktor's server pipeline rather than tied to one engine, and
the SERVER span carries the real route and status code. HikariCP/pgjdbc are instrumented too, one
CLIENT span per statement, without a single line of code written for either.

The application's own log line for the *same* request, read back from the running process's
stdout (JSON, via `platform:observability`'s existing Logback fragment):

```json
{"timestamp":1787915978206,"level":"INFO","loggerName":"org.flywaydb.core.FlywayExecutor",
 "mdc":{"trace_id":"7411a6a7b2d8326b119c018a8da88ee9","trace_flags":"03","span_id":"7e76b27f8f5be582"},
 "formattedMessage":"Database: ******** (PostgreSQL 17.11)"}
```

`trace_id`/`span_id` in this log line are **exactly** the SERVER span's own — not a coincidence,
checked against a second, independent request too (a different `/api/v1/health` call produced a
different pair, `db22a54f...`/`37ed3c2c...`, again matching that request's own SERVER span
exactly). The agent's MDC injection wins outright: nowhere in either run did a self-minted,
`IdGenerator`-derived value from `TraceContext` appear in a log line once the agent was attached.

**And that is the problem, not the reassurance it looks like.** The same `/api/v1/health/ready`
response carried a `traceparent` response header —
`00-01a04818c31f72dfa7accce86fe75af7-8a8de57b354e182d-01` — rendered by this codebase's own HTTP
boundary code from `TraceContext.current()`. Its trace id, `01a04818…`, matches **neither**
SERVER span shown above. `TraceContext`'s own coroutine-context value and the agent's real span
for the identical request are now two different identities, and nothing before this run would
have said so: the log line looks like the network of correlated telemetry ADR-056 wanted, right
up until someone tries to match it against the `traceparent` the same request actually sent
onward.

## What this means for the plan

Adding the agent as configured here is not "log-shipping on top of an unchanged model," which is
what Section 6 of the plan assumed when it framed this as low-risk ("платформа уже несёт
trace_id/span_id... это ровно то, что OTel-инструментирование подключает как транспорт, не
переделка существующей модели логирования"). The agent's MDC injection silently supersedes
`TraceContext`'s own values for any request the agent actually instruments, which — now measured,
not assumed — is every HTTP request Ktor CIO serves. `TraceContext.current()` stops being "the
request's identity" and becomes a second, disagreeing one that nothing has told to stop existing.

This is a real fork, not a detail to quietly settle while writing ADR-067 — three options were
put to the user: source `TraceContext` from the agent's active span, disable the agent's MDC
injection outright, or rename the agent's own MDC keys so both coexist without colliding.

## 2026-08-28 — resolution: rename the agent's MDC keys, leave `TraceContext` untouched

Chosen over the other two: no code change to `TraceContext`/`TraceHeader`/`IdGenerator`, no new
compile dependency, and the agent's real span data is still fully available — just under
different MDC keys, so a log line can still be cross-referenced to its span in Grafana Cloud by
those keys, rather than by the two this codebase already owns.

The mechanism: `otel.instrumentation.common.logging.trace-id-key` and `...span-id-key` (system
properties; `OTEL_INSTRUMENTATION_COMMON_LOGGING_TRACE_ID_KEY` /
`OTEL_INSTRUMENTATION_COMMON_LOGGING_SPAN_ID_KEY` as environment variables), which retarget the
agent's Logback MDC injection away from `trace_id`/`span_id` — confirmed to be the current,
already-merged property names for the agent version this spike ran
(`open-telemetry/opentelemetry-java-instrumentation` PR
[#18851](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/18851), merged
2026-06-12, ahead of the v2.31.1 release this spike used) — **not** the earlier, superseded
`otel.instrumentation.common.logging.trace-id`/`span-id` form an abandoned sibling PR proposed
first (#18765, closed in #18851's favour).

**Not independently re-run against a live process**, unlike the collision finding above: Docker
Desktop stopped responding partway through this investigation (`failed to connect to the docker
API`, no `Docker Desktop`/`com.docker.backend` process left running) — outside this session's
control, and not something to force past. What is verified is the property names themselves,
against the actual merged pull request rather than a blog post's paraphrase of it. Re-running the
same reproduction command block above with
`OTEL_INSTRUMENTATION_COMMON_LOGGING_TRACE_ID_KEY=otel_trace_id` and
`OTEL_INSTRUMENTATION_COMMON_LOGGING_SPAN_ID_KEY=otel_span_id` added, and confirming the JSON log
line then carries both `trace_id`/`span_id` (unchanged, `TraceContext`'s own) and
`otel_trace_id`/`otel_span_id` (the agent's) side by side, is the one thing worth doing before
trusting this in production — flagged here rather than silently assumed.
